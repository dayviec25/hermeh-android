package com.hermeh.android.feature.files

import androidx.annotation.DrawableRes
import com.hermeh.android.R

object FileIcons {
    @DrawableRes
    fun resolve(name: String): Int {
        val base = name.substringAfterLast('/').replace(Regex(":\\d+(:\\d+)?$"), "").lowercase()
        byName[base]?.let { return it }
        if (base.startsWith("tsconfig.") && base.endsWith(".json")) return R.drawable.fileicon_tsconfig
        val segments = base.split('.')
        for (index in 1 until segments.size) {
            byExtension[segments.drop(index).joinToString(".")]?.let { return it }
        }
        return R.drawable.fileicon_default
    }

    private val byName: Map<String, Int> = buildMap {
        fun put(icon: Int, vararg names: String) = names.forEach { put(it, icon) }
        put(R.drawable.fileicon_babel, ".babelrc", ".babelrc.json", "babel.config.js", "babel.config.cjs", "babel.config.json", "babel.config.mjs")
        put(R.drawable.fileicon_bash, ".bash_profile", ".bashrc", ".zprofile", ".zshenv", ".zshrc")
        put(R.drawable.fileicon_browserslist, ".browserslistrc")
        put(R.drawable.fileicon_docker, ".dockerignore", "dockerfile", "compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml", "docker-compose.override.yml")
        put(R.drawable.fileicon_eslint, ".eslintignore", ".eslintrc", ".eslintrc.cjs", ".eslintrc.js", ".eslintrc.json", ".eslintrc.yaml", ".eslintrc.yml", "eslint.config.js", "eslint.config.cjs", "eslint.config.mjs", "eslint.config.mts", "eslint.config.ts")
        put(R.drawable.fileicon_git, ".gitattributes", ".gitignore", ".gitkeep", ".gitmodules")
        put(R.drawable.fileicon_oxc, ".oxlintrc.json")
        put(R.drawable.fileicon_postcss, ".postcssrc", ".postcssrc.json", ".postcssrc.yaml", ".postcssrc.yml", "postcss.config.js", "postcss.config.cjs", "postcss.config.mjs", "postcss.config.ts")
        put(R.drawable.fileicon_prettier, ".prettierignore", ".prettierrc", ".prettierrc.json", ".prettierrc.cjs", ".prettierrc.js", ".prettierrc.mjs", ".prettierrc.toml", ".prettierrc.yaml", ".prettierrc.yml", "prettier.config.js", "prettier.config.cjs", "prettier.config.mjs")
        put(R.drawable.fileicon_stylelint, ".stylelintignore", ".stylelintrc", ".stylelintrc.cjs", ".stylelintrc.js", ".stylelintrc.json", ".stylelintrc.mjs", ".stylelintrc.yaml", ".stylelintrc.yml", "stylelint.config.js", "stylelint.config.cjs", "stylelint.config.mjs")
        put(R.drawable.fileicon_terraform, ".terraform.lock.hcl")
        put(R.drawable.fileicon_agents, "agents.md")
        put(R.drawable.fileicon_biome, "biome.json", "biome.jsonc")
        put(R.drawable.fileicon_bun, "bun.lock", "bun.lockb", "bunfig.toml")
        put(R.drawable.fileicon_claude, "claude.md")
        put(R.drawable.fileicon_ruby, "gemfile", "rakefile")
        put(R.drawable.fileicon_nextjs, "next.config.js", "next.config.mjs", "next.config.mts", "next.config.ts")
        put(R.drawable.fileicon_package, "package.json")
        put(R.drawable.fileicon_pnpm, "pnpm-lock.yaml", "pnpm-workspace.yaml")
        put(R.drawable.fileicon_readme, "readme.md")
        put(R.drawable.fileicon_svgo, "svgo.config.js", "svgo.config.cjs", "svgo.config.mjs", "svgo.config.ts")
        put(R.drawable.fileicon_tailwind, "tailwind.config.js", "tailwind.config.cjs", "tailwind.config.mjs", "tailwind.config.ts")
        put(R.drawable.fileicon_tsconfig, "tsconfig.json")
        put(R.drawable.fileicon_vite, "vite.config.js", "vite.config.mjs", "vite.config.mts", "vite.config.ts")
        put(R.drawable.fileicon_webpack, "webpack.config.js", "webpack.config.babel.js", "webpack.config.cjs", "webpack.config.mjs", "webpack.config.ts")
    }

    private val byExtension: Map<String, Int> = buildMap {
        fun put(icon: Int, vararg names: String) = names.forEach { put(it, icon) }
        put(R.drawable.fileicon_zip, "7z", "bz2", "gz", "jar", "rar", "tar", "tgz", "zip")
        put(R.drawable.fileicon_astro, "astro")
        put(R.drawable.fileicon_image, "avif", "bmp", "gif", "icns", "ico", "jpeg", "jpg", "png", "webp")
        put(R.drawable.fileicon_vscode, "code-workspace")
        put(R.drawable.fileicon_bash, "bash", "fish", "sh", "zsh")
        put(R.drawable.fileicon_c, "c", "h")
        put(R.drawable.fileicon_cpp, "cc", "cpp", "cxx", "hh", "hpp", "hxx", "inl")
        put(R.drawable.fileicon_css, "css", "less", "postcss")
        put(R.drawable.fileicon_table, "csv", "tsv")
        put(R.drawable.fileicon_typescript, "cts", "mts", "ts")
        put(R.drawable.fileicon_database, "db", "sql", "sqlite", "sqlite3")
        put(R.drawable.fileicon_text, "env", "env.development", "env.local", "env.production", "ini", "txt")
        put(R.drawable.fileicon_font, "eot", "woff", "woff2")
        put(R.drawable.fileicon_ruby, "erb", "rake", "rb")
        put(R.drawable.fileicon_go, "go")
        put(R.drawable.fileicon_graphql, "gql", "graphql")
        put(R.drawable.fileicon_html, "htm", "html")
        put(R.drawable.fileicon_javascript, "js", "mjs")
        put(R.drawable.fileicon_json, "json", "jsonc")
        put(R.drawable.fileicon_react, "jsx", "tsx")
        put(R.drawable.fileicon_markdown, "md", "mdx", "mdx.tsx")
        put(R.drawable.fileicon_python, "py", "pyi", "pyw", "pyx")
        put(R.drawable.fileicon_rust, "rs")
        put(R.drawable.fileicon_sass, "sass", "scss")
        put(R.drawable.fileicon_svelte, "svelte")
        put(R.drawable.fileicon_svg, "svg")
        put(R.drawable.fileicon_swift, "swift")
        put(R.drawable.fileicon_terraform, "tf", "tfstate", "tfvars")
        put(R.drawable.fileicon_vue, "vue")
        put(R.drawable.fileicon_wasm, "wasm")
        put(R.drawable.fileicon_yml, "yaml", "yml")
        put(R.drawable.fileicon_zig, "zig")
    }
}
