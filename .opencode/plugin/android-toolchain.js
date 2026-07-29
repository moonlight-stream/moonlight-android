export const AndroidToolchain = async () => ({
  "shell.env": async (_input, output) => {
    if (!process.env.HOME) {
      return
    }

    const toolchainRoot = `${process.env.HOME}/.local/share/moonlight-android-toolchain`
    output.env.JAVA_HOME = `${toolchainRoot}/jdk`
    output.env.ANDROID_HOME = `${toolchainRoot}/sdk`
    output.env.ANDROID_SDK_ROOT = `${toolchainRoot}/sdk`
    output.env.PATH = `${toolchainRoot}/jdk/bin:${toolchainRoot}/sdk/platform-tools:${output.env.PATH ?? process.env.PATH ?? ""}`
  },
})
