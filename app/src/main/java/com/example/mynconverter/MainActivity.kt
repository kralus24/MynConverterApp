package com.example.mynconverter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.navigation.NavigationView
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun Context.getFileName(uri: Uri): String {
    var name = "Bilinmeyen dosya"
    this.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if(cursor.moveToFirst()){
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if(nameIndex > -1) {
                name = cursor.getString(nameIndex)
            }
        }
    }
    return name
}

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerViewFiles: RecyclerView
    private lateinit var spinnerFormat: android.widget.Spinner
    private lateinit var tvFileCount: android.widget.TextView
    private lateinit var btnAddFile: android.widget.Button
    private lateinit var btnComplete: android.widget.Button
    private lateinit var btnClearAll: android.widget.Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    private val selectedFiles = ArrayList<Uri>()
    private lateinit var fileAdapter: FileAdapter
    private var conversionFolder: File? = null
    private var isConverting = false
    private var selectionMimeType: String? = null

    private val videoFormats = listOf("MP4", "AVI", "MKV", "MOV", "WMV")
    private val audioFormats = listOf("MP3", "WAV", "FLAC", "AAC", "WMA")
    private val imageFormats = listOf("JPG", "PNG", "GIF", "WEBP", "TIFF", "ICO")
    private val documentFormats = listOf("PDF", "DOCX", "TXT")
    private val archiveFormats = listOf("ZIP")

    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            createConversionFolder()
        } else {
            Toast.makeText(this, "Depolama izni verilmedi, uygulama düzgün çalışmayabilir.", Toast.LENGTH_LONG).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                if (selectedFiles.isEmpty()) {
                    val firstFileUri = data.clipData?.getItemAt(0)?.uri ?: data.data
                    firstFileUri?.let {
                        val mimeType = contentResolver.getType(it)
                        selectionMimeType = when {
                            mimeType?.startsWith("video/") == true -> "video/*"
                            mimeType?.startsWith("audio/") == true -> "audio/*"
                            mimeType?.startsWith("image/") == true -> "image/*"
                            else -> "*/*"
                        }
                    }
                }
                if (data.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) {
                        selectedFiles.add(data.clipData!!.getItemAt(i).uri)
                    }
                } else if (data.data != null) {
                    selectedFiles.add(data.data!!)
                }
                fileAdapter.notifyDataSetChanged()
                updateFileCount()
                updateUiForSelectionState()
                Toast.makeText(this, "${selectedFiles.size} dosya seçildi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupUI()
        setupClickListeners()
        checkAndRequestPermissions() // İzin isteme burada ÇAĞIRILIR
        updateUiForSelectionState()

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.open_nav, R.string.close_nav)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_converted_files -> {
                    openConvertedFilesFolder()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_clear_all -> {
                    clearAllFiles()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
    } // **onCreate METODU BURADA BİTER**

    // **TÜM DİĞER FONKSİYONLAR onCreate'İN DIŞINDA, SINIFIN (CLASS) İÇİNDE OLMALIDIR**

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            createConversionFolder()
        }
    }

    private fun updateUiForSelectionState() {
        val formatList: List<String>
        if (selectedFiles.isEmpty()) {
            selectionMimeType = null
            formatList = listOf("Lütfen bir dosya seçin")
            spinnerFormat.isEnabled = false
        } else {
            formatList = when (selectionMimeType) {
                "video/*" -> videoFormats
                "audio/*" -> audioFormats
                "image/*" -> imageFormats
                else -> documentFormats + archiveFormats
            }
            spinnerFormat.isEnabled = true
        }
        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item, formatList).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerFormat.adapter = spinnerAdapter
    }

    private fun selectFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = selectionMimeType ?: "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        filePickerLauncher.launch(intent)
    }

    private fun clearSelectedFiles() {
        selectedFiles.clear()
        fileAdapter.notifyDataSetChanged()
        updateFileCount()
        updateUiForSelectionState()
    }

    private fun createConversionFolder(): File {
        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloadsFolder, "MynConverter")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        conversionFolder = folder
        return folder
    }

    private fun openConvertedFilesFolder() {
        val targetFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MynConverter")
        if (!targetFolder.exists()) {
            Toast.makeText(this, "Klasör henüz oluşturulmadı.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val authority = "com.android.externalstorage.documents"
            val rootId = "primary"
            val folderPath = "Download/MynConverter"
            val documentId = "$rootId:$folderPath"
            val documentUri = DocumentsContract.buildDocumentUri(authority, documentId)
            intent.setDataAndType(documentUri, "vnd.android.document/directory")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", targetFolder)
                intent.setDataAndType(uri, "vnd.android.document/directory")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Klasör açılamadı. Cihazınızda uygun bir dosya yöneticisi bulunmuyor olabilir.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializeViews() {
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles)
        spinnerFormat = findViewById(R.id.spinnerFormat)
        tvFileCount = findViewById(R.id.tvFileCount)
        btnAddFile = findViewById(R.id.btnAddFile)
        btnComplete = findViewById(R.id.btnComplete)
        btnClearAll = findViewById(R.id.btnClearAll)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.open_nav, R.string.close_nav)
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return when (item.itemId) {
            R.id.menu_view_files -> {
                openConvertedFilesFolder()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI() {
        fileAdapter = FileAdapter(selectedFiles)
        recyclerViewFiles.layoutManager = LinearLayoutManager(this)
        recyclerViewFiles.adapter = fileAdapter
    }
    private fun setupClickListeners() {
        btnAddFile.setOnClickListener { if (!isConverting) selectFiles() }
        btnComplete.setOnClickListener {
            if (isConverting) {
                Toast.makeText(this, "Dönüştürme işlemi devam ediyor...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedFiles.isEmpty()) {
                Toast.makeText(this, "Lütfen en az bir dosya seçin", Toast.LENGTH_SHORT).show()
            } else {
                val targetFormat = spinnerFormat.selectedItem.toString()
                showConversionDialog(targetFormat)
            }
        }
        btnClearAll.setOnClickListener { if (!isConverting) clearSelectedFiles() }
    }

    private fun showConversionDialog(format: String) {
        val message = "${selectedFiles.size} dosyayı $format formatına dönüştürmek istiyor musunuz?"
        android.app.AlertDialog.Builder(this)
            .setTitle("Dönüştür")
            .setMessage(message)
            .setPositiveButton("Evet") { _, _ -> startRealConversion(format) }
            .setNegativeButton("Hayır", null)
            .setCancelable(false)
            .show()
    }

    private fun startRealConversion(format: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Dosya yazma izni olmadan dönüştürme yapılamaz.", Toast.LENGTH_LONG).show()
            return
        }

        isConverting = true
        btnComplete.isEnabled = false
        btnAddFile.isEnabled = false
        btnClearAll.isEnabled = false
        Toast.makeText(this, "$format dönüştürülüyor...", Toast.LENGTH_LONG).show()
        CoroutineScope(Dispatchers.IO).launch {
            var successCount = 0
            for (uri in selectedFiles) {
                val fileName = getFileName(uri)
                val baseName = fileName.substringBeforeLast(".")
                val convertedFileName = "$baseName.${format.lowercase()}"
                val outputFile = File(conversionFolder, convertedFileName)
                val success = convertFile(uri, outputFile, format)
                if(success) successCount++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "$successCount/${selectedFiles.size} dosya başarıyla dönüştürüldü!", Toast.LENGTH_LONG).show()
                resetUI()
                clearSelectedFiles()
            }
        }
    }

    private fun convertFile(inputUri: Uri, outputFile: File, targetFormat: String): Boolean {
        return try {
            val inputStream = contentResolver.openInputStream(inputUri)
            inputStream?.use { input ->
                when (targetFormat.uppercase()) {
                    "PDF" -> convertToPdf(input, outputFile)
                    "DOCX" -> convertToDocx(input, outputFile)
                    "TXT" -> convertToTxt(input, outputFile)
                    "JPG", "PNG", "WEBP" -> convertImage(input, outputFile, targetFormat)
                    "TIFF", "ICO", "GIF" -> convertImageWithFfmpeg(inputUri, outputFile)
                    "MP3", "M4A", "AAC", "WMA", "WAV", "FLAC" -> convertAudio(inputUri, outputFile, targetFormat)
                    "MP4", "MOV", "MKV", "AVI", "WMV" -> convertVideo(inputUri, outputFile, targetFormat)
                    "ZIP" -> convertToZip(input, outputFile)
                    else -> return false
                }
            } ?: false
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun convertToPdf(inputStream: InputStream, outputFile: File): Boolean {
        return try {
            val pdfWriter = PdfWriter(outputFile)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)
            val textContent = inputStream.bufferedReader().use { it.readText() }
            document.add(Paragraph(textContent))
            document.close()
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun convertToDocx(inputStream: InputStream, outputFile: File): Boolean {
        return try {
            val textContent = inputStream.bufferedReader().use { it.readText() }
            val document = XWPFDocument()
            document.createParagraph().createRun().setText(textContent)
            FileOutputStream(outputFile).use { document.write(it) }
            document.close()
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun convertToTxt(inputStream: InputStream, outputFile: File): Boolean {
        return try {
            val textContent = inputStream.bufferedReader().use { it.readText() }
            FileOutputStream(outputFile).use { it.write(textContent.toByteArray()) }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun convertImage(inputStream: InputStream, outputFile: File, targetFormat: String): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeStream(inputStream)
            FileOutputStream(outputFile).use { outputStream ->
                when (targetFormat.uppercase()) {
                    "JPG", "JPEG" -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    "PNG" -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    "WEBP" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, outputStream)
                        } else {
                            @Suppress("DEPRECATION")
                            bitmap.compress(Bitmap.CompressFormat.WEBP, 90, outputStream)
                        }
                    }
                    else -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
            }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun convertImageWithFfmpeg(inputUri: Uri, outputFile: File): Boolean {
        return try {
            val inputPath = getFilePathFromUri(inputUri) ?: return false
            val command = "-i \"$inputPath\" \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun convertAudio(inputUri: Uri, outputFile: File, targetFormat: String): Boolean {
        return try {
            val inputPath = getFilePathFromUri(inputUri) ?: return false
            val command = when (targetFormat.uppercase()) {
                "MP3" -> "-i \"$inputPath\" -codec:a libmp3lame -qscale:a 2 \"${outputFile.absolutePath}\""
                "M4A", "AAC" -> "-i \"$inputPath\" -c:a aac -b:a 192k \"${outputFile.absolutePath}\""
                "WMA" -> "-i \"$inputPath\" -c:a wmav2 -b:a 192k \"${outputFile.absolutePath}\""
                "WAV" -> "-i \"$inputPath\" -c:a pcm_s16le \"${outputFile.absolutePath}\""
                "FLAC" -> "-i \"$inputPath\" -c:a flac \"${outputFile.absolutePath}\""
                else -> return false
            }
            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode)
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun convertVideo(inputUri: Uri, outputFile: File, targetFormat: String): Boolean {
        return try {
            val inputPath = getFilePathFromUri(inputUri) ?: return false
            val command = when (targetFormat.uppercase()) {
                "MP4" -> "-i \"$inputPath\" -c:v libx264 -c:a aac \"${outputFile.absolutePath}\""
                "MOV", "MKV" -> "-i \"$inputPath\" -c copy \"${outputFile.absolutePath}\""
                "AVI" -> "-i \"$inputPath\" -c:v libx264 -c:a mp3 \"${outputFile.absolutePath}\""
                "WMV" -> "-i \"$inputPath\" -c:v msmpeg4 -c:a wmav2 \"${outputFile.absolutePath}\""
                else -> return false
            }
            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode)
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun convertToZip(inputStream: InputStream, outputFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                val entry = ZipEntry("content.bin")
                zos.putNextEntry(entry)
                inputStream.copyTo(zos)
                zos.closeEntry()
            }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            val file = File(cacheDir, "temp_file_${System.currentTimeMillis()}")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            file.absolutePath
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun resetUI() {
        isConverting = false
        btnComplete.isEnabled = true
        btnAddFile.isEnabled = true
        btnClearAll.isEnabled = true
    }

    private fun clearAllFiles() {
        conversionFolder?.let { folder ->
            if (folder.exists()) {
                var deletedCount = 0
                folder.listFiles()?.forEach {
                    if (it.delete()) deletedCount++
                }
                Toast.makeText(this, "$deletedCount dosya silindi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFileCount() {
        tvFileCount.text = "Seçilen dosya: ${selectedFiles.size}"
    }
}

class FileAdapter(private val files: List<Uri>) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    class FileViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FileViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val textView = holder.view.findViewById<android.widget.TextView>(android.R.id.text1)
        textView.text = holder.view.context.getFileName(files[position])
        textView.setTextColor(holder.view.context.getColor(android.R.color.white))
    }

    override fun getItemCount() = files.size
}