package dev.zodable

object Files {
    fun String.pythonCompatible() = this.replace("-", "_")

    const val ZOD_DESCRIPTION = "Auto-generated zod project from Kotlin using zodable"

    fun generatePyProjectToml(
        name: String,
        version: String,
    ): String =
        """
        import sys
        import toml
        import subprocess
        
        project = {
            "name": "$name",
            "version": "$version",
            "description": "Auto-generated Pydantic project from Kotlin using zodable",
            "dependencies": [],
            "requires-python": ">=3.9"
        }
        
        with open("requirements.txt", "r") as f:
            deps = [line.strip() for line in f if line.strip() and not line.startswith("#")]
        project["dependencies"] = [dep.replace("==", ">=") for dep in deps]

        pyproject = {
            "project": project,
            "build-system": {
                "requires": ["setuptools", "wheel"],
                "build-backend": "setuptools.build_meta"
            },
            "tool": {
                "setuptools": {
                    "package-data": {
                        "${name.pythonCompatible()}": ["py.typed"]
                    }
                },
                "mypy": {
                    "strict": True
                }
            }
        }
        
        with open("pyproject.toml", "w") as f:
            toml.dump(pyproject, f)
        """.trimIndent()
}
