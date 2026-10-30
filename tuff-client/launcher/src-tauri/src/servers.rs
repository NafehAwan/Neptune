//! Server List Ping: the same status query the in-game server list uses.

use anyhow::{bail, Context, Result};
use serde::Serialize;
use serde_json::Value;
use std::time::{Duration, Instant};
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

const TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct PingResult {
    pub online: u32,
    pub max: u32,
    pub motd: String,
    pub version: String,
    pub latency_ms: u32,
    pub favicon: Option<String>,
}

pub fn write_varint(buf: &mut Vec<u8>, value: i32) {
    let mut v = value as u32;
    loop {
        if v & !0x7f == 0 {
            buf.push(v as u8);
            return;
        }
        buf.push(((v & 0x7f) | 0x80) as u8);
        v >>= 7;
    }
}

pub async fn read_varint<R: AsyncRead + Unpin>(reader: &mut R) -> Result<i32> {
    let mut value: u32 = 0;
    for i in 0..5 {
        let byte = reader.read_u8().await?;
        value |= ((byte & 0x7f) as u32) << (7 * i);
        if byte & 0x80 == 0 {
            return Ok(value as i32);
        }
    }
    bail!("VarInt is too long")
}

fn write_string(buf: &mut Vec<u8>, s: &str) {
    write_varint(buf, s.len() as i32);
    buf.extend_from_slice(s.as_bytes());
}

pub fn packet(id: i32, body: &[u8]) -> Vec<u8> {
    let mut inner = Vec::with_capacity(body.len() + 2);
    write_varint(&mut inner, id);
    inner.extend_from_slice(body);
    let mut out = Vec::with_capacity(inner.len() + 3);
    write_varint(&mut out, inner.len() as i32);
    out.extend(inner);
    out
}

pub fn handshake(host: &str, port: u16) -> Vec<u8> {
    let mut body = Vec::new();
    write_varint(&mut body, 767);
    write_string(&mut body, host);
    body.extend_from_slice(&port.to_be_bytes());
    write_varint(&mut body, 1);
    packet(0x00, &body)
}

/// `host[:port]`, following `_minecraft._tcp` SRV records when no port is given.
async fn resolve(address: &str) -> Result<(String, u16)> {
    let address = address.trim();
    if let Some((host, port)) = address.rsplit_once(':') {
        if let Ok(port) = port.parse::<u16>() {
            return Ok((host.to_string(), port));
        }
    }
    if let Ok(resolver) = hickory_resolver::TokioAsyncResolver::tokio_from_system_conf() {
        if let Ok(Ok(srv)) = tokio::time::timeout(
            Duration::from_secs(2),
            resolver.srv_lookup(format!("_minecraft._tcp.{address}")),
        )
        .await
        {
            if let Some(record) = srv.iter().next() {
                let target = record.target().to_utf8();
                return Ok((target.trim_end_matches('.').to_string(), record.port()));
            }
        }
    }
    Ok((address.to_string(), 25565))
}

pub async fn ping(address: &str) -> Result<PingResult> {
    let (host, port) = resolve(address).await?;
    let mut stream = tokio::time::timeout(TIMEOUT, TcpStream::connect((host.as_str(), port)))
        .await
        .context("server did not respond")??;
    stream.set_nodelay(true)?;
    tokio::time::timeout(TIMEOUT, async {
        let mut out = handshake(&host, port);
        out.extend(packet(0x00, &[]));
        stream.write_all(&out).await?;

        let _length = read_varint(&mut stream).await?;
        let _id = read_varint(&mut stream).await?;
        let len = read_varint(&mut stream).await?;
        if !(0..=1 << 21).contains(&len) {
            bail!("invalid status response");
        }
        let mut json = vec![0u8; len as usize];
        stream.read_exact(&mut json).await?;
        let status: Value = serde_json::from_slice(&json)?;

        let started = Instant::now();
        stream.write_all(&packet(0x01, &0i64.to_be_bytes())).await?;
        let latency = match read_varint(&mut stream).await {
            Ok(_) => started.elapsed().as_millis() as u32,
            Err(_) => 0,
        };
        Ok(PingResult {
            online: status["players"]["online"].as_u64().unwrap_or(0) as u32,
            max: status["players"]["max"].as_u64().unwrap_or(0) as u32,
            motd: strip_codes(&flatten_text(&status["description"])),
            version: strip_codes(status["version"]["name"].as_str().unwrap_or("")),
            latency_ms: latency,
            favicon: status["favicon"].as_str().map(String::from),
        })
    })
    .await
    .context("server timed out")?
}

/// Flattens a chat component (string, object or array) into plain text.
pub fn flatten_text(value: &Value) -> String {
    match value {
        Value::String(s) => s.clone(),
        Value::Array(items) => items.iter().map(flatten_text).collect(),
        Value::Object(map) => {
            let mut out = map.get("text").map(flatten_text).unwrap_or_default();
            if let Some(extra) = map.get("extra") {
                out.push_str(&flatten_text(extra));
            }
            out
        }
        _ => String::new(),
    }
}

/// Removes legacy `§x` formatting codes.
pub fn strip_codes(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else {
            out.push(c);
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn varint_encoding() {
        let cases: &[(i32, &[u8])] = &[
            (0, &[0x00]),
            (1, &[0x01]),
            (127, &[0x7f]),
            (128, &[0x80, 0x01]),
            (255, &[0xff, 0x01]),
            (25565, &[0xdd, 0xc7, 0x01]),
            (2097151, &[0xff, 0xff, 0x7f]),
            (-1, &[0xff, 0xff, 0xff, 0xff, 0x0f]),
        ];
        for (value, bytes) in cases {
            let mut buf = Vec::new();
            write_varint(&mut buf, *value);
            assert_eq!(&buf, bytes, "encoding {value}");
        }
    }

    #[tokio::test]
    async fn varint_roundtrip() {
        for value in [0, 1, 300, 25565, i32::MAX, -1] {
            let mut buf = Vec::new();
            write_varint(&mut buf, value);
            let mut slice = buf.as_slice();
            assert_eq!(read_varint(&mut slice).await.unwrap(), value);
        }
    }

    #[test]
    fn handshake_layout() {
        let p = handshake("a", 25565);
        // length, id 0, protocol 767 (2 bytes), string "a", port, next state
        assert_eq!(p, vec![8, 0x00, 0xff, 0x05, 1, b'a', 0x63, 0xdd, 1]);
    }

    #[test]
    fn motd_text() {
        let v = serde_json::json!({"text": "§aHello ", "extra": [{"text": "World"}, "!"]});
        assert_eq!(strip_codes(&flatten_text(&v)), "Hello World!");
    }
}
