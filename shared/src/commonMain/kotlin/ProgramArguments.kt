import com.toasterofbread.spmp.ProjectBuildConfig
import dev.toastbits.spms.socketapi.shared.SPMS_API_VERSION
import org.jetbrains.compose.resources.getString
import spmp.shared.generated.resources.Res
import spmp.shared.generated.resources.help_message
import spmp.shared.generated.resources.`version_message_non_release_$commit_$apiver_$split`
import spmp.shared.generated.resources.`version_message_release_$appver_$apiver_$split`
import spmp.shared.generated.resources.version_string

data class ProgramArguments(
    val no_auto_server: Boolean = false,
    val is_flatpak: Boolean = false
) {
    companion object {
        suspend fun parse(args: Array<String>): ProgramArguments? {
            fun onIllegalArgument(msg: String) {
                throw IllegalArgumentException("$msg Received arguments: ${args.toList()}")
            }

            var arguments: ProgramArguments = ProgramArguments()

            val iterator: Iterator<String> = args.iterator()
            while (iterator.hasNext()) {
                val split: List<String> = iterator.next().split('=', limit = 2)
                val name: String = split.first()
                val value: String? = split.getOrNull(1)

                when (name) {
                    "--help", "-h" -> {
                        println(getHelpMessage())
                        return null
                    }
                    "--version", "-v" -> {
                        println(getVersionMessage())
                        return null
                    }
                    "--disable-auto-server", "-ds" -> {
                        arguments = arguments.copy(no_auto_server = true)
                    }
                    "--flatpak" -> {
                        arguments = arguments.copy(is_flatpak = true)
                    }
                    else -> onIllegalArgument("Unknown argument '$name'.")
                }
            }

            return arguments
        }

        suspend fun getHelpMessage(): String =
            getString(Res.string.help_message) + "\n" + getVersionMessage()

        suspend fun getVersionMessage(split_lines: Boolean = false): String {
            val version_string: String = "v${getString(Res.string.version_string)}"
            val api_version_string: String = SPMS_API_VERSION.toString()
            val split_string: String = if (split_lines) "\n" else ""

            val on_release_commit: Boolean = ProjectBuildConfig.GIT_TAG == version_string
            if (on_release_commit) {
                return getString(Res.string.`version_message_release_$appver_$apiver_$split`)
                    .replace("\$appver", version_string)
                    .replace("\$apiver", api_version_string)
                    .replace("\$split", split_string)
            }
            else {
                return getString(Res.string.`version_message_non_release_$commit_$apiver_$split`)
                    .replace("\$commit", ProjectBuildConfig.GIT_COMMIT_HASH?.take(7).toString())
                    .replace("\$apiver", api_version_string)
                    .replace("\$split", split_string)
            }
        }
    }
}
