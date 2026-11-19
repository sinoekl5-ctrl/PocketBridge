package com.example.pocketbridge

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PocketBridgeHttpServer(private val ctx: Context, private val tree: Uri, private val token: String) :
    NanoHTTPD("0.0.0.0", 8765) {

    override fun serve(s: IHTTPSession): Response {
        if ((s.headers["authorization"] ?: "") != "Bearer $token")
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "Unauthorized"))
        return try {
            val root = DocumentFile.fromTreeUri(ctx, tree)
                ?: return json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", "Folder permission unavailable"))
            val path = s.parameters["path"]?.firstOrNull().orEmpty()
            when {
                s.method == Method.GET && s.uri == "/api/status" -> json(Response.Status.OK, JSONObject().put("status", "online"))
                s.method == Method.GET && s.uri == "/api/files" -> listing(root, path)
                s.method == Method.GET && s.uri == "/api/download" -> download(root, path)
                s.method == Method.DELETE && s.uri == "/api/delete" -> delete(root, path)
                s.method == Method.POST && s.uri == "/api/mkdir" -> mkdir(root, path, param(s, "name"))
                s.method == Method.POST && s.uri == "/api/rename" -> rename(root, path, param(s, "name"))
                s.method == Method.POST && s.uri == "/api/upload" -> upload(s, root, path, param(s, "name"))
                else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "Unknown endpoint"))
            }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "Operation failed"))
        }
    }

    private fun param(s: IHTTPSession, k: String) = s.parameters[k]?.firstOrNull().orEmpty()

    private fun resolve(root: DocumentFile, path: String): DocumentFile? {
        if (path.isBlank() || path == "/") return root
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.any { it == "." || it == ".." || it.contains('\\') }) return null
        var current = root
        for (part in parts) current = current.findFile(part) ?: return null
        return current
    }

    private fun listing(root: DocumentFile, path: String): Response {
        val dir = resolve(root, path) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "Folder not found"))
        if (!dir.isDirectory) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Not a folder"))
        val items = JSONArray()
        dir.listFiles().forEach { f ->
            items.put(JSONObject().put("name", f.name ?: "").put("path", listOf(path.trim('/'), f.name ?: "").filter { it.isNotBlank() }.joinToString("/"))
                .put("directory", f.isDirectory).put("size", f.length()).put("mimeType", f.type ?: JSONObject.NULL).put("lastModified", f.lastModified()))
        }
        return json(Response.Status.OK, JSONObject().put("path", path).put("items", items))
    }

    private fun download(root: DocumentFile, path: String): Response {
        val f = resolve(root, path) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "File not found"))
        if (!f.isFile) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Not a file"))
        val input = ctx.contentResolver.openInputStream(f.uri) ?: return json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", "Cannot read file"))
        return newChunkedResponse(Response.Status.OK, f.type ?: "application/octet-stream", input).apply {
            addHeader("Content-Disposition", "attachment; filename=\"${(f.name ?: "download").replace("\"", "")}\"")
        }
    }

    private fun delete(root: DocumentFile, path: String): Response {
        if (path.isBlank() || path == "/") return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Cannot delete shared root"))
        val f = resolve(root, path) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "Not found"))
        val ok = f.delete()
        return json(if (ok) Response.Status.OK else Response.Status.INTERNAL_ERROR, JSONObject().put("deleted", ok))
    }

    private fun mkdir(root: DocumentFile, path: String, name: String): Response {
        if (!validName(name)) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Invalid name"))
        val parent = resolve(root, path) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "Folder not found"))
        val made = parent.createDirectory(name) ?: return json(Response.Status.CONFLICT, JSONObject().put("error", "Cannot create folder"))
        return json(Response.Status.OK, JSONObject().put("created", made.name))
    }

    private fun rename(root: DocumentFile, path: String, name: String): Response {
        if (!validName(name)) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Invalid name"))
        val f = resolve(root, path) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "Not found"))
        val ok = f.renameTo(name)
        return json(if (ok) Response.Status.OK else Response.Status.INTERNAL_ERROR, JSONObject().put("renamed", ok))
    }

    private fun upload(s: IHTTPSession, root: DocumentFile, path: String, name: String): Response {
        if (!validName(name)) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Invalid filename"))
        val parent = resolve(root, path) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "Destination not found"))
        if (!parent.isDirectory) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Destination is not a folder"))
        val body = HashMap<String, String>()
        s.parseBody(body)
        val temp = body["postData"] ?: return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Expected raw file bytes"))
        val mime = s.headers["content-type"]?.substringBefore(';') ?: "application/octet-stream"
        val dest = parent.createFile(mime, name) ?: return json(Response.Status.CONFLICT, JSONObject().put("error", "Cannot create file"))
        ctx.contentResolver.openOutputStream(dest.uri, "w").use { out ->
            if (out == null) throw IllegalStateException("Cannot write destination")
            File(temp).inputStream().use { it.copyTo(out) }
        }
        return json(Response.Status.OK, JSONObject().put("uploaded", dest.name).put("size", dest.length()))
    }

    private fun validName(n: String) = n.isNotBlank() && n.length <= 180 && n != "." && n != ".." &&
        !n.contains('/') && !n.contains('\\') && !n.contains('\u0000')

    private fun json(status: Response.IStatus, obj: JSONObject) =
        newFixedLengthResponse(status, "application/json; charset=utf-8", obj.toString())
}
