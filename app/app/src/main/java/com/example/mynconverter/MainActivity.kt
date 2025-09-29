package com.example.mynconverter

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerViewFiles: RecyclerView
    private lateinit var spinnerFormat: android.widget.Spinner
    private lateinit var tvFileCount: android.widget.TextView
    private lateinit var btnAddFile: android.widget.Button
    private lateinit var btnComplete: android.widget.Button
    private lateinit var btnClearAll: android.widget.Button

    // Yeni eklenen değişkenler
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    private val selectedFiles = ArrayList<Uri>()
    private lateinit var fileAdapter: FileAdapter
    private var conversionFolder: File? = null
    private var isConverting = false

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                if (data.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) {
                        selectedFiles.add(data.clipData!!.getItemAt(i).uri)
                    }
                } else if (data.data != null) {
                    selectedFiles.add(data.data!!)
                }

                fileAdapter.notifyDataSetChanged()
                updateFileCount()
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
        createConversionFolder()

        // Hamburger menü için yeni kodlar
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
    }

    private fun initializeViews() {
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles)
        spinnerFormat = findViewById(R.id.spinnerFormat)
        tvFileCount = findViewById(R.id.tvFileCount)
        btnAddFile = findViewById(R.id.btnAddFile)
        btnComplete = findViewById(R.id.btnComplete)
        btnClearAll = findViewById(R.id.btnClearAll)
    }

    // Bu fonksiyon menüyü Action Bar'da gösterir
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // Bu fonksiyon menüdeki öğelere tıklama olaylarını yakalar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Hamburger menü butonuna tıklandığında çekmeceyi aç
                // Bu kodun çalışması için Action Bar'ın hamburger ikonunu göstermesi gerekir
                // Bu da "supportActionBar?.setDisplayHomeAsUpEnabled(true)" ile sağlanır
                // Not: Eğer üç çizgi yerine geri oku görüyorsan, bir üst aktiviteye geçmek için
                // manifestte parentActivityName tanımlanmış olabilir.
                true
            }
            R.id.menu_converted_files -> {
                openConvertedFilesFolder()
                true
            }
            R.id.menu_clear_all -> {
                clearAllFiles()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI() {
        val formats = arrayOf("PDF", "DOCX", "TXT", "JPG", "PNG", "MP3", "MP4", "M4A", "WEBP", "GIF", "ZIP")

        val spinnerAdapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, formats) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
                return view
            }
        }

        spinnerFormat.adapter = spinnerAdapter

        fileAdapter = FileAdapter(selectedFiles)
        recyclerViewFiles.layoutManager = LinearLayoutManager(this)
        recyclerViewFiles.adapter = fileAdapter
    }

        // Bu satır açılır menüdeki öğelerin görünümünü ayarlar ve yanındaki çubukları kaldırır.
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFormat.adapter = spinnerAdapter

        fileAdapter = FileAdapter(selectedFiles)
        recyclerViewFiles.layoutManager = LinearLayoutManager(this)
        recyclerViewFiles.adapter = fileAdapter
    }

        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_1) // Bu satırı değiştirin
        spinnerFormat.adapter = spinnerAdapter

        fileAdapter = FileAdapter(selectedFiles)
        recyclerViewFiles.layoutManager = LinearLayoutManager(this)
        recyclerViewFiles.adapter = fileAdapter
    }

    private fun setupClickListeners() {
        btnAddFile.setOnClickListener {
            if (!isConverting) selectFiles()
        }

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

        btnClearAll.setOnClickListener {
            if (!isConverting) clearSelectedFiles()
        }
    }

    private fun createConversionFolder(): File {
        val folder = File(getExternalFilesDir(null), "Dönüştürülenler")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        conversionFolder = folder
        return folder
    }

    private fun openConvertedFilesFolder() {
        conversionFolder?.let { folder ->
            if (folder.exists() && folder.listFiles()?.isNotEmpty() == true) {
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", folder)
                intent.setDataAndType(uri, "*/*")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Henüz dönüştürülmüş dosya yok", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        filePickerLauncher.launch(intent)
    }

    private fun showConversionDialog(format: String) {
        val message = "${selectedFiles.size} dosyayı $format formatına dönüştürmek istiyor musunuz?"
        android.app.AlertDialog.Builder(this)
            .setTitle("Dönüştür")
            .setMessage(message)
            .setPositiveButton("Evet") { _, _ ->
                startRealConversion(format)
            }
            .setNegativeButton("Hayır", null)
            .setCancelable(false)
            .show()
    }

    private fun startRealConversion(format: String) {
        isConverting = true
        btnComplete.isEnabled = false
        btnAddFile.isEnabled = false
        btnClearAll.isEnabled = false

        Toast.makeText(this, "$format dönüştürülüyor...", Toast.LENGTH_LONG).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((index, uri) in selectedFiles.withIndex()) {
                    val fileName = getFileName(this@MainActivity, uri)
                    val baseName = fileName.substringBeforeLast(".")
                    val convertedFileName = "$baseName.$format"
                    val outputFile = File(conversionFolder, convertedFileName)

                    // Dosya dönüşümünü gerçekleştir
                    val success = convertFile(uri, outputFile, format)

                    withContext(Dispatchers.Main) {
                        val progress = ((index + 1) * 100 / selectedFiles.size)
                        if (success) {
                            Toast.makeText(this@MainActivity,
                                "$fileName $format formatına dönüştürüldü (%$progress tamamlandı)",
                                Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity,
                                "$fileName dönüştürülürken hata oluştu",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "Tüm dosyalar dönüştürüldü! Menüden görüntüleyebilirsiniz.",
                        Toast.LENGTH_LONG).show()
                    resetUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "Dönüştürme hatası: ${e.message}",
                        Toast.LENGTH_LONG).show()
                    resetUI()
                }
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
                    "JPG", "PNG", "WEBP", "GIF" -> convertImage(input, outputFile, targetFormat)
                    "MP3", "M4A" -> convertAudio(inputUri, outputFile, targetFormat)
                    "MP4" -> convertVideo(inputUri, outputFile)
                    "ZIP" -> convertToZip(input, outputFile)
                    else -> {
                        // Desteklenmeyen formatlar için basit kopyalama
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                        true
                    }
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun convertToDocx(inputStream: InputStream, outputFile: File): Boolean {
        return try {
            val textContent = inputStream.bufferedReader().use { it.readText() }
            val document = XWPFDocument()
            document.createParagraph().createRun().setText(textContent)

            FileOutputStream(outputFile).use { fos ->
                document.write(fos)
            }
            document.close()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun convertToTxt(inputStream: InputStream, outputFile: File): Boolean {
        return try {
            val textContent = inputStream.bufferedReader().use { it.readText() }
            FileOutputStream(outputFile).use { it.write(textContent.toByteArray()) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun convertImage(inputStream: InputStream, outputFile: File, targetFormat: String): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = FileOutputStream(outputFile)

            when (targetFormat.uppercase()) {
                "JPG", "JPEG" -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                "PNG" -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                "WEBP" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 90, outputStream)
                    } else {
                        // Eski Android versiyonları için JPEG kullan
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                }
                "GIF" -> {
                    // GIF için basit bir dönüşüm
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                else -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            outputStream.close()
            true
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
                "M4A" -> "-i \"$inputPath\" -c:a aac -b:a 192k \"${outputFile.absolutePath}\""
                else -> return false
            }

            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun convertVideo(inputUri: Uri, outputFile: File): Boolean {
        return try {
            val inputPath = getFilePathFromUri(inputUri) ?: return false
            val command = "-i \"$inputPath\" -c:v libx264 -c:a aac \"${outputFile.absolutePath}\""

            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun convertToZip(inputStream: InputStream, outputFile: File): Boolean {
        return try {
            val content = inputStream.readBytes()
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                val entry = ZipEntry("content.bin")
                zos.putNextEntry(entry)
                zos.write(content)
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val file = File(cacheDir, "temp_file_${System.currentTimeMillis()}")
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun resetUI() {
        isConverting = false
        btnComplete.isEnabled = true
        btnAddFile.isEnabled = true
        btnClearAll.isEnabled = true
    }

    private fun clearSelectedFiles() {
        selectedFiles.clear()
        fileAdapter.notifyDataSetChanged()
        updateFileCount()
        Toast.makeText(this, "Seçilen dosyalar temizlendi", Toast.LENGTH_SHORT).show()
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

    private fun getFileName(context: android.content.Context, uri: Uri): String {
        var name = "Bilinmeyen dosya"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: "Bilinmeyen dosya"
            }
        }
        return name
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
        textView.text = getFileName(holder.view.context, files[position])
        textView.setTextColor(holder.view.context.getColor(android.R.color.white))
    }

    override fun getItemCount() = files.size

    private fun getFileName(context: android.content.Context, uri: Uri): String {
        var name = "Bilinmeyen dosya"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: "Bilinmeyen dosya"
            }
        }
        return name
    }
}