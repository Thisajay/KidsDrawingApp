package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var drawingView:DrawingView?= null
    private var mImageButtonCurrentPaint:ImageButton?= null
    var customProgressDialog:Dialog?=null

    private val openGalleryLauncher:ActivityResultLauncher<Intent> =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                result ->
                if (result.resultCode == RESULT_OK && result.data != null){
                    val imageBackground:ImageView=findViewById(R.id.id_bg)
                    imageBackground.setImageURI(result.data?.data)
                }
    }

    private val requestPermission:ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permission->
            permission.entries.forEach{
                val permissionName=it.key
                val isGranted=it.value
                if (isGranted){
                    Toast.makeText(this,"Permission granted now you can read the storage files.",Toast.LENGTH_LONG).show()
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)

                }else{
                    if (permissionName==Manifest.permission.READ_EXTERNAL_STORAGE)
                    {
                        Toast.makeText(this,"Permission not Granted.",Toast.LENGTH_LONG).show()

                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView= findViewById(R.id.drawingArea)
        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColor = findViewById<LinearLayout>(R.id.linearLayoutColor)
        mImageButtonCurrentPaint = linearLayoutPaintColor[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

        val ibBrush:ImageButton= findViewById(R.id.id_brush)
       ibBrush.setOnClickListener{
           showBrushSizeChooseDialog()
       }
        val idUndo: ImageButton = findViewById(R.id.idUndo)
        idUndo.setOnClickListener{
            drawingView?.onClickUndo()
        }
        val idSave:ImageButton=findViewById(R.id.idSave)
        idSave.setOnClickListener{
            if (isReadStorageAllowed()){
                lifecycleScope.launch{
                    val flDrawingView:FrameLayout = findViewById(R.id.drawingViewContainer)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }

        val ibGallery:ImageButton = findViewById(R.id.ibGallery)
        ibGallery.setOnClickListener{
            requestStoragePermission()
        }
    }

    private fun showBrushSizeChooseDialog(){
        val brushDialog= Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
       val smallBtn:ImageView = brushDialog.findViewById(R.id.id_small_brush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(7.toFloat())
            brushDialog.dismiss()
        }
        val mdBtn:ImageView = brushDialog.findViewById(R.id.id_md_brush)
        mdBtn.setOnClickListener{
            drawingView?.setSizeForBrush(15.toFloat())
            brushDialog.dismiss()
        }
        val lgBtn:ImageView = brushDialog.findViewById(R.id.id_lg_brush)
        lgBtn.setOnClickListener{
            drawingView?.setSizeForBrush(25.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()


    }

    fun paintClicked(view:View){
        if (view !== mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
            mImageButtonCurrentPaint?.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.pallet_normal))
            mImageButtonCurrentPaint= view
        }


    }

    private fun showRationalDialog(title:String, message:String){
        val builder:AlertDialog.Builder=AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){
                dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun isReadStorageAllowed():Boolean{
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationalDialog("kids Drawing App", "kids drawing App " + " needs to Access Your External Storage")
        }else{
            requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            Manifest.permission.WRITE_EXTERNAL_STORAGE

        }
    }

    private fun getBitmapFromView(view: View):Bitmap{
        val returnBitmap= Bitmap.createBitmap(view.width, view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnBitmap)
        val bgDrawable=view.background
        if (bgDrawable!=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
      return  returnBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?):String{
        var result= ""
        withContext(Dispatchers.IO){
            if (mBitmap!=null){
                try {
                    val bytes=ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90,bytes)

                    val f= File(externalCacheDir?.absoluteFile.toString()
                            + File.separator + "KidDrawingApp" + System.currentTimeMillis()/1000+".png" )

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result= f.absolutePath

                    runOnUiThread{
                        cancelProgressDialog()
                        if (result!=null){
                            Toast.makeText(this@MainActivity, "File saved successfully :$result", Toast.LENGTH_SHORT).show()
                        shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity, "Something Went wrong", Toast.LENGTH_SHORT).show()

                        }
                    }




                }catch (e:Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result

    }

    private  fun showProgressDialog(){
        customProgressDialog=Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.custom_progress_dialog)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        showProgressDialog()
        if (customProgressDialog!= null){
            customProgressDialog?.dismiss()
            customProgressDialog=null
        }
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path,uri->
            val shareIntent= Intent()
            shareIntent.action= Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type= "image/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }

}