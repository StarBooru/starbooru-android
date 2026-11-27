//! The administration's AI panel: each tool, how it runs, its models and
//! the machine the server runs on. Downloads of models are followed by
//! asking `admin_ai` again while `busy` says something is moving.

/// An AI tool of the server.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum AiTool {
    Tagging,
    Translation,
    Comics,
    People,
}

impl From<starbooru_core::AiTool> for AiTool {
    fn from(t: starbooru_core::AiTool) -> Self {
        use starbooru_core::AiTool as T;
        match t {
            T::Tagging => AiTool::Tagging,
            T::Translation => AiTool::Translation,
            T::Comics => AiTool::Comics,
            T::People => AiTool::People,
        }
    }
}

impl From<AiTool> for starbooru_core::AiTool {
    fn from(t: AiTool) -> Self {
        use starbooru_core::AiTool as T;
        match t {
            AiTool::Tagging => T::Tagging,
            AiTool::Translation => T::Translation,
            AiTool::Comics => T::Comics,
            AiTool::People => T::People,
        }
    }
}

/// Where a tool runs.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AiMode {
    Off,
    /// StarBooru's own models, on the server.
    Local,
    /// An OpenAI-compatible API, hosted or on the LAN.
    Api,
}

impl From<starbooru_core::AiMode> for AiMode {
    fn from(m: starbooru_core::AiMode) -> Self {
        use starbooru_core::AiMode as M;
        match m {
            M::Off => AiMode::Off,
            M::Local => AiMode::Local,
            M::Api => AiMode::Api,
        }
    }
}

impl From<AiMode> for starbooru_core::AiMode {
    fn from(m: AiMode) -> Self {
        use starbooru_core::AiMode as M;
        match m {
            AiMode::Off => M::Off,
            AiMode::Local => M::Local,
            AiMode::Api => M::Api,
        }
    }
}

/// How long an answer may take: about 1 s, 5 s or 30 s.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AiPreset {
    Fast,
    Balanced,
    Accurate,
}

impl From<starbooru_core::AiPreset> for AiPreset {
    fn from(p: starbooru_core::AiPreset) -> Self {
        use starbooru_core::AiPreset as P;
        match p {
            P::Fast => AiPreset::Fast,
            P::Balanced => AiPreset::Balanced,
            P::Accurate => AiPreset::Accurate,
        }
    }
}

impl From<AiPreset> for starbooru_core::AiPreset {
    fn from(p: AiPreset) -> Self {
        use starbooru_core::AiPreset as P;
        match p {
            AiPreset::Fast => P::Fast,
            AiPreset::Balanced => P::Balanced,
            AiPreset::Accurate => P::Accurate,
        }
    }
}

/// How far a tool is from answering.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AiToolState {
    Off,
    Ready,
    /// Its model is downloading; an installed one answers meanwhile.
    Installing,
    /// The last try failed; it tries again by itself.
    Failed,
}

impl From<starbooru_core::AiToolState> for AiToolState {
    fn from(s: starbooru_core::AiToolState) -> Self {
        use starbooru_core::AiToolState as S;
        match s {
            S::Off => AiToolState::Off,
            S::Ready => AiToolState::Ready,
            S::Installing => AiToolState::Installing,
            S::Failed => AiToolState::Failed,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum GpuKind {
    /// With its own memory.
    Dedicated,
    /// Built into the processor.
    Integrated,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Gpu {
    pub name: String,
    pub kind: GpuKind,
    pub vram_bytes: Option<u64>,
    /// What a model can use of it.
    pub vram_free_bytes: Option<u64>,
}

/// The machine the server runs on.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Hardware {
    /// `x86_64`, `aarch64`, …
    pub arch: String,
    /// On single-board computers ("Raspberry Pi 5 Model B").
    pub board: Option<String>,
    pub cpu: Option<String>,
    pub threads: u32,
    /// 0 when unknown.
    pub ram_total: u64,
    pub ram_available: u64,
    pub gpus: Vec<Gpu>,
}

impl From<starbooru_core::Hardware> for Hardware {
    fn from(h: starbooru_core::Hardware) -> Self {
        Self {
            arch: h.arch,
            board: h.board,
            cpu: h.cpu,
            threads: h.threads,
            ram_total: h.ram_total,
            ram_available: h.ram_available,
            gpus: h
                .gpus
                .into_iter()
                .map(|g| Gpu {
                    name: g.name,
                    kind: match g.kind {
                        starbooru_core::GpuKind::Dedicated => GpuKind::Dedicated,
                        starbooru_core::GpuKind::Integrated => GpuKind::Integrated,
                    },
                    vram_bytes: g.vram_bytes,
                    vram_free_bytes: g.vram_free_bytes,
                })
                .collect(),
        }
    }
}

/// Whether StarBooru's own models can run on the server.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiRuntimeStatus {
    pub ready: bool,
    /// Why not, in the server's English; `None` while it starts.
    pub reason: Option<String>,
    /// The graphics interface in use (`cuda`, `webgpu`).
    pub gpu: Option<String>,
}

/// How one tool is set up. Its key never comes back.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiToolSettings {
    pub mode: AiMode,
    pub preset: AiPreset,
    /// A model chosen by hand; empty follows the preset.
    pub model: String,
    pub api_url: String,
    pub api_model: String,
    pub api_key_set: bool,
}

/// What a preset means on this machine.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiPresetOption {
    pub preset: AiPreset,
    /// A model meets its time here.
    pub available: bool,
    /// The model it picks.
    pub model: Option<String>,
    pub est_ms: Option<u32>,
}

/// A download, in bytes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct AiInstallProgress {
    pub done: u64,
    pub total: u64,
}

/// A model in a tool's catalogue.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiModelOption {
    /// For `install_ai_model` and `AiToolUpdate::model`.
    pub id: String,
    pub name: String,
    /// Bytes to download.
    pub size: u64,
    pub ram_mb: u32,
    /// Benchmarks put it at the top of its field.
    pub high_accuracy: bool,
    pub est_ms: u32,
    /// `est_ms` was timed on this machine, not estimated.
    pub measured: bool,
    /// It runs on the graphics card.
    pub gpu: bool,
    pub installed: bool,
    /// It fits in this machine's memory.
    pub fits: bool,
    /// Downloading, or waiting to.
    pub progress: Option<AiInstallProgress>,
    /// Why the last download failed.
    pub error: Option<String>,
}

/// One tool of the panel.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiToolStatus {
    pub tool: AiTool,
    pub settings: AiToolSettings,
    pub presets: Vec<AiPresetOption>,
    pub models: Vec<AiModelOption>,
    /// The model answering now.
    pub active: Option<String>,
    pub state: AiToolState,
    pub error: Option<String>,
}

impl From<starbooru_core::AiToolStatus> for AiToolStatus {
    fn from(t: starbooru_core::AiToolStatus) -> Self {
        Self {
            tool: t.tool.into(),
            settings: AiToolSettings {
                mode: t.settings.mode.into(),
                preset: t.settings.preset.into(),
                model: t.settings.model,
                api_url: t.settings.api_url,
                api_model: t.settings.api_model,
                api_key_set: t.settings.api_key_set,
            },
            presets: t
                .presets
                .into_iter()
                .map(|p| AiPresetOption {
                    preset: p.preset.into(),
                    available: p.available,
                    model: p.model,
                    est_ms: p.est_ms,
                })
                .collect(),
            models: t
                .models
                .into_iter()
                .map(|m| AiModelOption {
                    id: m.id,
                    name: m.name,
                    size: m.size,
                    ram_mb: m.ram_mb,
                    high_accuracy: m.high_accuracy,
                    est_ms: m.est_ms,
                    measured: m.measured,
                    gpu: m.gpu,
                    installed: m.installed,
                    fits: m.fits,
                    progress: m.progress.map(|p| AiInstallProgress {
                        done: p.done,
                        total: p.total,
                    }),
                    error: m.error,
                })
                .collect(),
            active: t.active,
            state: t.state.into(),
            error: t.error,
        }
    }
}

/// The whole panel.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiAdminStatus {
    pub hardware: Hardware,
    pub runtime: AiRuntimeStatus,
    /// New uploads are tagged by the AI unless the uploader says.
    pub autotag: bool,
    /// The language new images' text is translated into; empty is off.
    pub auto_translate: String,
    pub tools: Vec<AiToolStatus>,
    /// Something changes by itself soon: the runtime starting or a model
    /// downloading. Ask `admin_ai` again in a moment while it's true.
    pub busy: bool,
}

/// Whether the panel is about to change by itself, as starbooru-web's
/// `logic::admin::busy` reads it.
fn busy(s: &starbooru_core::AiAdminStatus) -> bool {
    let starting = !s.runtime.ready && s.runtime.reason.is_none();
    let downloading = s
        .tools
        .iter()
        .any(|t| t.models.iter().any(|m| m.progress.is_some()));
    starting || downloading
}

impl From<starbooru_core::AiAdminStatus> for AiAdminStatus {
    fn from(s: starbooru_core::AiAdminStatus) -> Self {
        let busy = busy(&s);
        Self {
            hardware: s.hardware.into(),
            runtime: AiRuntimeStatus {
                ready: s.runtime.ready,
                reason: s.runtime.reason,
                gpu: s.runtime.gpu,
            },
            autotag: s.autotag,
            auto_translate: s.auto_translate,
            tools: s.tools.into_iter().map(Into::into).collect(),
            busy,
        }
    }
}

/// How one tool is to run. Models it needs start downloading.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiToolUpdate {
    pub mode: AiMode,
    pub preset: AiPreset,
    /// A model id from the tool's catalogue; empty follows the preset.
    #[uniffi(default)]
    pub model: String,
    /// The API's address and model, both needed in API mode.
    #[uniffi(default)]
    pub api_url: String,
    #[uniffi(default)]
    pub api_model: String,
    /// `None` keeps the key, `""` clears it, anything else replaces it.
    #[uniffi(default)]
    pub api_key: Option<String>,
}

impl From<AiToolUpdate> for starbooru_core::AiToolUpdate {
    fn from(u: AiToolUpdate) -> Self {
        Self {
            mode: u.mode.into(),
            preset: u.preset.into(),
            model: u.model,
            api_url: u.api_url,
            api_model: u.api_model,
            api_key: u.api_key,
        }
    }
}

/// `AiToolSettings` as an update that changes nothing, keeping the key, for
/// a screen to change one part of.
#[uniffi::export]
pub fn ai_tool_update_of(settings: AiToolSettings) -> AiToolUpdate {
    AiToolUpdate {
        mode: settings.mode,
        preset: settings.preset,
        model: settings.model,
        api_url: settings.api_url,
        api_model: settings.api_model,
        api_key: None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn status(json: serde_json::Value) -> AiAdminStatus {
        let s: starbooru_core::AiAdminStatus = serde_json::from_value(json).unwrap();
        s.into()
    }

    fn tool(name: &str, progress: Option<serde_json::Value>) -> serde_json::Value {
        serde_json::json!({
            "tool": name,
            "settings": {"mode": "local", "preset": "fast", "model": "", "api_url": "",
                         "api_model": "", "api_key_set": false},
            "presets": [{"preset": "fast", "available": true, "model": "m1", "est_ms": 800}],
            "models": [{"id": "m1", "name": "M1", "size": 10, "ram_mb": 100, "est_ms": 800,
                        "measured": false, "gpu": false, "installed": false, "fits": true,
                        "progress": progress, "error": null}],
            "active": null, "state": "installing", "error": null
        })
    }

    #[test]
    fn the_panel_reads_its_newer_tools_and_says_when_it_moves() {
        let hardware = serde_json::json!({"arch": "x86_64", "board": null, "cpu": "Ryzen",
            "threads": 16, "ram_total": 1, "ram_available": 1,
            "gpus": [{"name": "RTX", "kind": "dedicated", "vram_bytes": 8}]});
        let panel = status(serde_json::json!({
            "hardware": hardware, "runtime": {"ready": true, "reason": null, "gpu": "cuda"},
            "autotag": true, "auto_translate": "",
            "tools": [tool("tagging", Some(serde_json::json!({"done": 1, "total": 10})))],
            "more_tools": [tool("people", None)]
        }));
        assert_eq!(
            panel.tools.iter().map(|t| t.tool).collect::<Vec<_>>(),
            [AiTool::Tagging, AiTool::People]
        );
        assert!(panel.busy);
        assert_eq!(panel.hardware.gpus[0].kind, GpuKind::Dedicated);
        assert_eq!(panel.tools[0].state, AiToolState::Installing);
        // Still, with the runtime ready: nothing moves.
        let still = status(serde_json::json!({
            "hardware": hardware, "runtime": {"ready": true, "reason": null, "gpu": null},
            "autotag": false, "auto_translate": "en", "tools": [tool("tagging", None)]
        }));
        assert!(!still.busy);
        // Starting without a word of why not: it moves.
        let starting = status(serde_json::json!({
            "hardware": hardware, "runtime": {"ready": false, "reason": null, "gpu": null},
            "autotag": false, "auto_translate": "", "tools": []
        }));
        assert!(starting.busy);
    }

    #[test]
    fn an_update_of_the_settings_keeps_the_key() {
        let settings = AiToolSettings {
            mode: AiMode::Api,
            preset: AiPreset::Balanced,
            model: String::new(),
            api_url: "http://192.168.1.5:11434/v1".into(),
            api_model: "llava".into(),
            api_key_set: true,
        };
        let update = ai_tool_update_of(settings);
        assert_eq!(update.api_key, None);
        let json = serde_json::to_value(starbooru_core::AiToolUpdate::from(update)).unwrap();
        assert_eq!(json["mode"], "api");
        assert_eq!(json["api_key"], serde_json::Value::Null);
    }
}
