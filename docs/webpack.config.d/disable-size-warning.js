// Disable webpack performance hints for WASM assets
// Skiko and Compose WASM binaries exceed default size limits by design
config.performance = {
  hints: false
};
