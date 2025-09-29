package com.example.mynconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileViewerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fileAdapter: FileViewerAdapter
    private val fileList = ArrayList<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_viewer)

        supportActionBar?.title = "Dönüştürülen Dosyalar"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadConvertedFiles()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadConvertedFiles() {
        fileList.clear()

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val conversionFolder = File(downloadsDir, "MynConverter")

            if (conversionFolder.exists() && conversionFolder.isDirectory) {
                val files = conversionFolder.listFiles()
                if (files != null) {
                    // Boş olmayan dosyaları filtrele ve tarihe göre sırala
                    fileList.addAll(files.filter { it.isFile && it.length() > 0 }
                        .sortedByDescending { it.lastModified() })

                    if (fileList.isNotEmpty()) {
                        fileAdapter = FileViewerAdapter(fileList) { file ->
                            openFile(file)
                        }
                        recyclerView.adapter = fileAdapter
                    } else {
                        Toast.makeText(this, "Dönüştürülmüş dosya bulunamadı", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Klasör boş veya erişilemiyor", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Klasör yoksa oluştur
                if (conversionFolder.mkdirs()) {
                    Toast.makeText(this, "Dönüştürülmüş dosya klasörü oluşturuldu", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Henüz dönüştürülmüş dosya yok", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Dosyalara erişim izni gerekli", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Dosyalar yüklenirken hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)

            // MIME type'ı dosya uzantısına göre belirle
            val mimeType = getMimeTypeFromExtension(file.extension) ?: "*/*"

            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Intent'i handle edebilecek bir uygulama var mı kontrol et
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Bu dosya türünü açabilecek uygulama bulunamadı", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Dosya açılırken hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeTypeFromExtension(extension: String): String? {
        return when (extension.lowercase(Locale.getDefault())) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "webp" -> "image/webp"
            "zip" -> "application/zip"
            else -> null
        }
    }

    // FileViewerAdapter inner class olarak tanımlandı
    inner class FileViewerAdapter(private val files: List<File>, private val onFileClick: (File) -> Unit) :
        RecyclerView.Adapter<FileViewerAdapter.FileViewHolder>() {

        inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileName: TextView = view.findViewById(R.id.textViewFileName)
            val fileDate: TextView = view.findViewById(R.id.textViewFileDate)
            // Eğer textViewFileSize yoksa, bu satırı kaldırın veya layout'a ekleyin
            // val fileSize: TextView = view.findViewById(R.id.textViewFileSize)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_file, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val file = files[position]
            holder.fileName.text = file.name
            holder.fileDate.text = formatDate(file.lastModified())
            // holder.fileSize.text = formatFileSize(file.length()) // Eğer textViewFileSize yoksa bu satırı kaldırın
            holder.itemView.setOnClickListener {
                onFileClick(file)
            }
        }

        override fun getItemCount() = files.size

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }
    }
}