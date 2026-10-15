use std::path::PathBuf;

/// On-disk layout. Libraries, assets and runtimes are shared between
/// profiles; every profile gets its own game directory under `instances/`.
#[derive(Clone)]
pub struct Paths {
    pub root: PathBuf,
}

impl Paths {
    pub fn new() -> Self {
        let root = std::env::var_os("TUFF_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                dirs::data_dir()
                    .unwrap_or_else(|| PathBuf::from("."))
                    .join("tuff-client")
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
