package com.bubble.vpn

import android.os.Environment
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class FtpServer(private val port: Int = 2222) {

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        if (running) return
        running = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    executor.execute { handleClient(client) }
                }
            } catch (_: Exception) {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }

    private fun handleClient(client: Socket) {
        val rootDir = Environment.getExternalStorageDirectory()
        var currentDir = rootDir
        var passiveServer: ServerSocket? = null
        var renameFromFile: File? = null

        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8), true)

            fun reply(code: Int, message: String) {
                writer.print("$code $message\r\n")
                writer.flush()
            }

            reply(220, "Service ready")

            while (running && !client.isClosed) {
                val line = reader.readLine() ?: break
                val parts = line.split(" ", limit = 2)
                val cmd = parts[0].uppercase(Locale.US)
                val arg = if (parts.size > 1) parts[1].trim() else ""

                when (cmd) {
                    "USER" -> reply(331, "User name okay, need password")
                    "PASS" -> reply(230, "User logged in, proceed")
                    "SYST" -> reply(215, "UNIX Type: L8")
                    "FEAT" -> {
                        writer.print("211-Features:\r\n UTF8\r\n PASV\r\n SIZE\r\n211 End\r\n")
                        writer.flush()
                    }
                    "OPTS" -> reply(200, "UTF8 mode enabled")
                    "TYPE" -> reply(200, "Type set to $arg")
                    "NOOP" -> reply(200, "OK")
                    "PWD", "XPWD" -> {
                        val rel = getRelativePath(rootDir, currentDir)
                        reply(257, "\"$rel\" is current directory")
                    }
                    "CWD", "XCWD" -> {
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (target.exists() && target.isDirectory) {
                            currentDir = target
                            reply(250, "Directory changed")
                        } else {
                            reply(550, "Failed to change directory")
                        }
                    }
                    "CDUP", "XCUP" -> {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canonicalPath.startsWith(rootDir.canonicalPath)) {
                            currentDir = parent
                        } else {
                            currentDir = rootDir
                        }
                        reply(250, "Directory changed")
                    }
                    "PASV" -> {
                        passiveServer?.close()
                        passiveServer = ServerSocket(0)
                        val pasvPort = passiveServer.localPort
                        val ip = (getLocalIpAddress() ?: "127.0.0.1").replace('.', ',')
                        val p1 = pasvPort / 256
                        val p2 = pasvPort % 256
                        reply(227, "Entering Passive Mode ($ip,$p1,$p2)")
                    }
                    "LIST", "NLST" -> {
                        val ps = passiveServer
                        if (ps == null) {
                            reply(425, "Use PASV first")
                        } else {
                            reply(150, "Opening data connection")
                            val dataSocket = ps.accept()
                            val dataOut = PrintWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8), true)
                            val files = currentDir.listFiles() ?: emptyArray()
                            val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                            for (f in files) {
                                if (cmd == "NLST") {
                                    dataOut.print("${f.name}\r\n")
                                } else {
                                    val perms = if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                                    val size = if (f.isDirectory) 4096L else f.length()
                                    val dateStr = dateFormat.format(Date(f.lastModified()))
                                    dataOut.print("$perms 1 owner group $size $dateStr ${f.name}\r\n")
                                }
                            }
                            dataOut.flush()
                            dataSocket.close()
                            ps.close()
                            passiveServer = null
                            reply(226, "Transfer complete")
                        }
                    }
                    "RETR" -> {
                        val ps = passiveServer
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (ps == null) {
                            reply(425, "Use PASV first")
                        } else if (!target.exists() || !target.isFile) {
                            reply(550, "File not found")
                        } else {
                            reply(150, "Opening binary data connection")
                            val dataSocket = ps.accept()
                            FileInputStream(target).use { input ->
                                dataSocket.getOutputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            dataSocket.close()
                            ps.close()
                            passiveServer = null
                            reply(226, "Transfer complete")
                        }
                    }
                    "STOR" -> {
                        val ps = passiveServer
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (ps == null) {
                            reply(425, "Use PASV first")
                        } else {
                            reply(150, "Ready to receive file")
                            val dataSocket = ps.accept()
                            FileOutputStream(target).use { output ->
                                dataSocket.getInputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            dataSocket.close()
                            ps.close()
                            passiveServer = null
                            reply(226, "Transfer complete")
                        }
                    }
                    "DELE" -> {
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (target.exists() && target.isFile && target.delete()) {
                            reply(250, "File deleted")
                        } else {
                            reply(550, "Delete failed")
                        }
                    }
                    "MKD", "XMKD" -> {
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (target.mkdirs()) {
                            reply(257, "\"${target.name}\" created")
                        } else {
                            reply(550, "Create directory failed")
                        }
                    }
                    "RMD", "XRMD" -> {
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (target.exists() && target.isDirectory && target.delete()) {
                            reply(250, "Directory removed")
                        } else {
                            reply(550, "Remove directory failed")
                        }
                    }
                    "RNFR" -> {
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (target.exists()) {
                            renameFromFile = target
                            reply(350, "Ready for RNTO")
                        } else {
                            reply(550, "File not found")
                        }
                    }
                    "RNTO" -> {
                        val from = renameFromFile
                        val to = resolvePath(rootDir, currentDir, arg)
                        if (from != null && from.renameTo(to)) {
                            renameFromFile = null
                            reply(250, "Rename successful")
                        } else {
                            reply(550, "Rename failed")
                        }
                    }
                    "SIZE" -> {
                        val target = resolvePath(rootDir, currentDir, arg)
                        if (target.exists() && target.isFile) {
                            reply(213, target.length().toString())
                        } else {
                            reply(550, "Could not get file size")
                        }
                    }
                    "QUIT" -> {
                        reply(221, "Goodbye")
                        break
                    }
                    else -> reply(502, "Command not implemented")
                }
            }
        } catch (_: Exception) {
        } finally {
            try { passiveServer?.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun getRelativePath(root: File, current: File): String {
        val rootPath = root.canonicalPath
        val currPath = current.canonicalPath
        return if (currPath.startsWith(rootPath)) {
            val sub = currPath.substring(rootPath.length).replace('\\', '/')
            if (sub.isEmpty()) "/" else sub
        } else {
            "/"
        }
    }

    private fun resolvePath(root: File, current: File, arg: String): File {
        val clean = arg.trim()
        val candidate = if (clean.startsWith("/")) {
            File(root, clean.removePrefix("/"))
        } else {
            File(current, clean)
        }
        return try {
            if (candidate.canonicalPath.startsWith(root.canonicalPath)) candidate else root
        } catch (_: Exception) {
            root
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
