package com.insyncwithfoo.pyright.runner

import com.insyncwithfoo.pyright.PyrightCommand
import com.insyncwithfoo.pyright.PyrightDiagnosticSeverity
import com.insyncwithfoo.pyright.configuration.AllConfigurations
import com.insyncwithfoo.pyright.path
import com.insyncwithfoo.pyright.pyrightConfigurations
import com.insyncwithfoo.pyright.pyrightExecutable
import com.insyncwithfoo.pyright.sdkPath
import com.insyncwithfoo.pyright.toPathIfItExists
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path


private object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Path {
        throw NotImplementedError("The deserializer should not be used")
    }
}


@Serializable
internal data class FileCommand(
    @Serializable(with = PathSerializer::class)
    val executable: Path,
    @Serializable(with = PathSerializer::class)
    val target: Path,
    val projectPath: String,
    val extraArguments: List<String>
) : PyrightCommand() {
    
    override val workingDirectory by ::projectPath
    
    override val fragments: List<String>
        get() = listOf(
            executable.toString(),
            *extraArguments.toTypedArray(),
            target.toString()
        )
    
    companion object {
        
        internal fun create(
            configurations: AllConfigurations,
            executable: Path,
            target: Path,
            projectPath: Path,
            interpreterPath: Path
        ): FileCommand {
            val configurationFile = configurations.configurationFile
            
            val argumentForProject = configurationFile ?: projectPath
            val extraArguments: MutableList<String> = mutableListOf(
                "--outputjson",
                "--project", argumentForProject.toString(),
                "--pythonpath", interpreterPath.toString()
            )
            
            if (configurations.minimumSeverityLevel != PyrightDiagnosticSeverity.INFORMATION) {
                extraArguments.add("--level")
                extraArguments.add(configurations.minimumSeverityLevel.name)
            }
            
            return FileCommand(executable, target, projectPath.toString(), extraArguments)
        }
        
        fun create(project: Project, file: VirtualFile): FileCommand? {
            val configurations = project.pyrightConfigurations
            
            val filePath = file.path.toPathIfItExists() ?: return null
            val projectPath = project.path ?: return null
            val executable = project.pyrightExecutable ?: return null
            val interpreterPath = project.sdkPath ?: return null
            
            return create(configurations, executable, filePath, projectPath, interpreterPath)
        }
        
        fun create(file: PsiFile): FileCommand? {
            return create(file.project, file.virtualFile)
        }
        
    }
    
}