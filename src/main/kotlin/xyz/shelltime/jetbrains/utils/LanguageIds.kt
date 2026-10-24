package xyz.shelltime.jetbrains.utils

/**
 * Maps IDE file types to VS Code language identifiers, the ids the other ShellTime editor
 * plugins report and the web merges on, so one language isn't split across editors
 */
object LanguageIds {
    /** Lowercased IntelliJ FileType names */
    private val fileTypeIds = mapOf(
        "kotlin" to "kotlin",
        "java" to "java",
        "javascript" to "javascript",
        "jsx harmony" to "javascriptreact",
        "typescript" to "typescript",
        "typescript jsx" to "typescriptreact",
        "python" to "python",
        "go" to "go",
        "rust" to "rust",
        "ruby" to "ruby",
        "php" to "php",
        "swift" to "swift",
        "scala" to "scala",
        "groovy" to "groovy",
        "dart" to "dart",
        "lua" to "lua",
        "c#" to "csharp",
        "html" to "html",
        "css" to "css",
        "scss" to "scss",
        "less" to "less",
        "xml" to "xml",
        "json" to "json",
        "json5" to "json",
        "yaml" to "yaml",
        "toml" to "toml",
        "markdown" to "markdown",
        "sql" to "sql",
        "graphql" to "graphql",
        "vue.js" to "vue",
        "svelte" to "svelte",
        "shell script" to "shellscript",
        "dockerfile" to "dockerfile",
        "makefile" to "makefile",
        "properties" to "properties",
        "protobuf" to "proto",
        "plain_text" to "plaintext"
    )

    /** Lowercased file extensions, for file types above that don't say (TextMate, C/C++) */
    private val extensionIds = mapOf(
        "ts" to "typescript",
        "mts" to "typescript",
        "cts" to "typescript",
        "tsx" to "typescriptreact",
        "js" to "javascript",
        "mjs" to "javascript",
        "cjs" to "javascript",
        "jsx" to "javascriptreact",
        "py" to "python",
        "pyi" to "python",
        "rs" to "rust",
        "rb" to "ruby",
        "go" to "go",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "java" to "java",
        "swift" to "swift",
        "scala" to "scala",
        "cs" to "csharp",
        "c" to "c",
        "h" to "c",
        "cpp" to "cpp",
        "cc" to "cpp",
        "cxx" to "cpp",
        "hpp" to "cpp",
        "hh" to "cpp",
        "hxx" to "cpp",
        "m" to "objective-c",
        "mm" to "objective-cpp",
        "sh" to "shellscript",
        "bash" to "shellscript",
        "zsh" to "shellscript",
        "md" to "markdown",
        "mdx" to "mdx",
        "yml" to "yaml",
        "yaml" to "yaml",
        "json" to "json",
        "jsonc" to "jsonc",
        "toml" to "toml",
        "xml" to "xml",
        "html" to "html",
        "htm" to "html",
        "css" to "css",
        "scss" to "scss",
        "less" to "less",
        "sql" to "sql",
        "graphql" to "graphql",
        "gql" to "graphql",
        "vue" to "vue",
        "svelte" to "svelte",
        "dart" to "dart",
        "lua" to "lua",
        "php" to "php",
        "gradle" to "groovy",
        "groovy" to "groovy",
        "tf" to "terraform",
        "proto" to "proto",
        "txt" to "plaintext"
    )

    /** Lowercased file names without an extension */
    private val fileNameIds = mapOf(
        "dockerfile" to "dockerfile",
        "makefile" to "makefile"
    )

    /**
     * @param fileTypeName The IDE's FileType name, e.g. "TypeScript JSX"
     * @param extension The file extension without the dot, if any
     * @param fileName The file name
     */
    fun languageId(fileTypeName: String, extension: String?, fileName: String): String {
        val typeName = fileTypeName.lowercase()
        fileTypeIds[typeName]?.let { return it }

        val ext = extension?.lowercase()
        if (!ext.isNullOrEmpty()) {
            return extensionIds[ext] ?: ext
        }
        return fileNameIds[fileName.lowercase()] ?: typeName
    }
}
