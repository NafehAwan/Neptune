use std::path::PathBuf;

/// On-disk layout. Libraries, assets and runtimes are shared between
/// profiles; every profile gets its own game directory under `instances/`.
#[derive(Clone)]
pub struct Paths {
    pub root: PathBuf,
}

impl Paths {
    pub fn new() -> Self {
        let root = std::env::var_os("GLADE_HOME")
            .or_else(|| std::env::var_os("TUFF_HOME"))
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                let data = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
                let root = data.join("glade");
                // The launcher used to be called Tuff Client; carry its data over once.
                let old = data.join("tuff-client");
                if !root.exists() && old.is_dir() {
                    let _ = std::fs::rename(&old, &root);
                }
                root
            });
        Self { root }
    }

    pub fn versions(&self) -> PathBuf {
        self.root.join("versions")
    }
    pub fn libraries(&self) -> PathBuf {
        self.root.join("libraries")
    }
    pub fn assets(&self) -> PathBuf {
        self.root.join("assets")
    }
    pub fn runtimes(&self) -> PathBuf {
        self.root.join("runtimes")
    }
    pub fn instances(&self) -> PathBuf {
        self.root.join("instances")
    }
    pub fn instance(&self, profile_id: &str) -> PathBuf {
        self.instances().join(profile_id)
    }
    pub fn natives(&self, version_id: &str) -> PathBuf {
        self.root.join("natives").join(version_id)
    }
    pub fn file(&self, name: &str) -> PathBuf {
        self.root.join(name)
    }
}
