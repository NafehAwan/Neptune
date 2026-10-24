/// Total physical memory in MiB (Linux reads /proc/meminfo; elsewhere a
/// conservative 8 GiB guess).
pub fn total_memory_mb() -> u32 {
    #[cfg(target_os = "linux")]
    if let Ok(info) = std::fs::read_to_string("/proc/meminfo") {
        if let Some(kb) = info
            .lines()
            .find(|l| l.starts_with("MemTotal:"))
            .and_then(|l| l.split_whitespace().nth(1))
            .and_then(|v| v.parse::<u64>().ok())
        {
            return (kb / 1024) as u32;
        }
    }
    8192
}

/// Half of system memory, kept between 1 and 4 GiB. More heap than this
/// rarely helps a client and lengthens GC pauses.
pub fn suggested_memory_mb() -> u32 {
    (total_memory_mb() / 2).clamp(1024, 4096) / 256 * 256
}
