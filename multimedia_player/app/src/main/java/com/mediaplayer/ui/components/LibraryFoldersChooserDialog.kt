package com.mediaplayer.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.mediaplayer.data.model.FolderRow

data class FolderTreeNode(
    val folderName: String,
    val folderPath: String,
    val displayPath: String,
    val localCount: Int,
    var totalCount: Int,
    val level: Int,
    val children: MutableList<FolderTreeNode> = mutableListOf(),
    var isExpanded: Boolean = false
)

enum class CheckState {
    CHECKED, UNCHECKED, PARTIAL
}

fun formatShortPath(path: String): String {
    val clean = path.trimEnd('/')
    val parts = clean.split('/')
    return if (parts.size > 4) {
        "${parts.first()}/${parts[1]}/…/${parts.last()}"
    } else {
        clean
    }
}

fun computeRecursiveTotals(node: FolderTreeNode): Int {
    val subTotal = node.children.sumOf { computeRecursiveTotals(it) }
    node.totalCount = node.localCount + subTotal
    return node.totalCount
}

fun buildFolderTree(folders: List<FolderRow>): List<FolderTreeNode> {
    val rootMap = mutableMapOf<String, FolderTreeNode>()

    for (f in folders) {
        val path = f.folderPath.trimEnd('/')
        val relPath = if (path.startsWith("/storage/emulated/0/")) {
            path.removePrefix("/storage/emulated/0/")
        } else if (path.startsWith("/storage/")) {
            path.substringAfter("/storage/").substringAfter("/")
        } else {
            path.removePrefix("/")
        }

        val name = f.folderName.ifBlank { path.substringAfterLast('/') }
        val node = FolderTreeNode(
            folderName = name,
            folderPath = f.folderPath,
            displayPath = formatShortPath(relPath),
            localCount = f.trackCount,
            totalCount = f.trackCount,
            level = 0
        )
        rootMap[f.folderPath] = node
    }

    val resultNodes = mutableListOf<FolderTreeNode>()
    for (node in rootMap.values) {
        var parentFound = false
        for (other in rootMap.values) {
            if (node.folderPath != other.folderPath && node.folderPath.startsWith(other.folderPath)) {
                other.children.add(node)
                parentFound = true
                break
            }
        }
        if (!parentFound) {
            resultNodes.add(node)
        }
    }

    fun assignLevels(n: FolderTreeNode, currentLevel: Int) {
        val nextLevel = if (n.children.size == 1 && n.localCount == 0) currentLevel else currentLevel
        n.children.forEach { child ->
            assignLevels(child, nextLevel + 1)
        }
    }

    resultNodes.forEach { root ->
        computeRecursiveTotals(root)
        assignLevels(root, 0)
    }

    return resultNodes.sortedBy { it.displayPath }
}

fun getCheckState(node: FolderTreeNode, selectedSet: Set<String>): CheckState {
    val selfChecked = selectedSet.contains(node.folderPath) || selectedSet.contains(node.displayPath)
    if (node.children.isEmpty()) {
        return if (selfChecked) CheckState.CHECKED else CheckState.UNCHECKED
    }

    val childStates = node.children.map { getCheckState(it, selectedSet) }
    val allChecked = selfChecked && childStates.all { it == CheckState.CHECKED }
    val allUnchecked = !selfChecked && childStates.all { it == CheckState.UNCHECKED }

    return when {
        allChecked -> CheckState.CHECKED
        allUnchecked -> CheckState.UNCHECKED
        else -> CheckState.PARTIAL
    }
}

fun toggleNodeSelection(node: FolderTreeNode, selectedSet: MutableSet<String>, isChecked: Boolean) {
    fun applyAll(n: FolderTreeNode, check: Boolean) {
        if (check) {
            selectedSet.add(n.folderPath)
            selectedSet.add(n.displayPath)
        } else {
            selectedSet.remove(n.folderPath)
            selectedSet.remove(n.displayPath)
        }
        n.children.forEach { applyAll(it, check) }
    }
    applyAll(node, isChecked)
}

@Composable
fun LibraryFoldersChooserDialog(
    allFolders: List<FolderRow>,
    currentlySelectedFolders: Set<String>,
    isFirstRun: Boolean = false,
    onSaveAndScan: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFolders by remember { mutableStateOf(currentlySelectedFolders.toMutableSet()) }
    val treeNodes = remember(allFolders) { buildFolderTree(allFolders) }

    var expandedPaths by remember { mutableStateOf(mutableSetOf<String>()) }

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        val docFile = DocumentFile.fromTreeUri(context, uri)
        if (docFile == null || !docFile.canRead()) {
            Toast.makeText(context, "Android doesn't allow this folder. Pick a folder inside it.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        val rawPath = uri.path ?: ""
        if (rawPath.contains("primary:Download") || rawPath.contains("primary:Android")) {
            Toast.makeText(context, "Android doesn't allow this folder. Pick a folder inside it.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        val relPath = if (rawPath.contains("primary:")) rawPath.substringAfter("primary:") else docFile.name ?: ""
        if (relPath.isNotBlank()) {
            selectedFolders = (selectedFolders + relPath + docFile.uri.toString()).toMutableSet()
            Toast.makeText(context, "Added folder: $relPath", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14171A),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderSpecial, null, tint = Color(0xFFFF5722), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Library Folders", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }

                    IconButton(onClick = { documentTreeLauncher.launch(null) }) {
                        Icon(Icons.Rounded.Add, "Add Folder", tint = Color(0xFFFF5722), modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFirstRun) "Choose the folders with your music. You can change this anytime in Settings." else "Selected folders determine which music appears across your library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        },
        text = {
            if (allFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No music folders detected on device", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    fun renderNode(node: FolderTreeNode) {
                        val checkState = getCheckState(node, selectedFolders)
                        val isExpanded = expandedPaths.contains(node.folderPath) || checkState == CheckState.PARTIAL

                        item(key = node.folderPath) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (node.level * 16).dp)
                                    .clickable {
                                        val newSet = selectedFolders.toMutableSet()
                                        toggleNodeSelection(node, newSet, checkState != CheckState.CHECKED)
                                        selectedFolders = newSet
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (node.children.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val newExpanded = expandedPaths.toMutableSet()
                                            if (isExpanded) newExpanded.remove(node.folderPath) else newExpanded.add(node.folderPath)
                                            expandedPaths = newExpanded
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.ChevronRight,
                                            contentDescription = "Expand",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(28.dp))
                                }

                                TriStateCheckbox(
                                    state = when (checkState) {
                                        CheckState.CHECKED -> ToggleableState.On
                                        CheckState.UNCHECKED -> ToggleableState.Off
                                        CheckState.PARTIAL -> ToggleableState.Indeterminate
                                    },
                                    onClick = {
                                        val newSet = selectedFolders.toMutableSet()
                                        toggleNodeSelection(node, newSet, checkState != CheckState.CHECKED)
                                        selectedFolders = newSet
                                    }
                                )

                                Icon(
                                    Icons.Rounded.Folder, null,
                                    tint = if (checkState != CheckState.UNCHECKED) Color(0xFFFF5722) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(node.folderName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                    val countText = if (node.children.isNotEmpty()) "${node.localCount} here · ${node.totalCount} total" else "${node.localCount} songs"
                                    val pathText = if (node.displayPath.isNotBlank()) " · ${node.displayPath}" else ""
                                    Text("$countText$pathText", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                                }
                            }
                        }

                        if (isExpanded && node.children.isNotEmpty()) {
                            node.children.forEach { child -> renderNode(child) }
                        }
                    }

                    treeNodes.forEach { rootNode -> renderNode(rootNode) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAndScan(selectedFolders) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save and Scan", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
