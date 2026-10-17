package com.youme.memoria.imageViewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yalantis.ucrop.UCrop
import com.youme.memoria.Album.deleteAlert
import com.youme.memoria.Gallery.GalleryRepository
import com.youme.memoria.Gallery.GalleryViewModel
import com.youme.memoria.R
import com.youme.memoria.search.SearchResultCache
import com.youme.memoria.search.searchActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class imageViewerActivity : AppCompatActivity(){


    private lateinit var Adatper : ImagePagerAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.imageview_activity)
        Adatper = ImagePagerAdapter()

        findViewById<ImageButton>(R.id.imageButton2).setOnClickListener {
            finishAfterTransition()
        }


        setupViewPager()
        setupUi()
        setupPadding()
        setupBottomAppBar()
    }

    private fun setupUi(){
        val viewpager2= findViewById<ViewPager2>(R.id.viewpager)


        findViewById<ImageButton>(R.id.imageButton7).setOnClickListener {
            val intent = Intent(this@imageViewerActivity, searchActivity::class.java)
            intent.putExtra("uri",Adatper.peek(viewpager2.currentItem)?.uri.toString())
            startActivity(intent)
        }

    }
    private fun setupViewPager(){

        val viewpager2= findViewById<ViewPager2>(R.id.viewpager)

        viewpager2.adapter = Adatper

        val tappedPosition = intent.getIntExtra("pos",0)
        val isFromSearch = intent.getBooleanExtra("from_search",false)


        lifecycleScope.launch {
            if (isFromSearch) {
                val searchData = SearchResultCache.searchResults
                if (searchData != null) {
                    Adatper.submitData(PagingData.from(searchData))
                }
            } else {
                val viewModel = GalleryViewModel(GalleryRepository(contentResolver))
                viewModel.imageViewerFlow(tappedPosition).collectLatest {
                    Adatper.submitData(it)
                }
            }

        }
        var isPositionSet = false
        Adatper.addOnPagesUpdatedListener {
            if (!isPositionSet && Adatper.itemCount > tappedPosition) {
                isPositionSet = true
                viewpager2.post {
                    viewpager2.setCurrentItem(tappedPosition, false)
                }
            }
        }


    }


    private fun setupBottomAppBar(){
        val viewpager2= findViewById<ViewPager2>(R.id.viewpager)
        val bottomBar  = findViewById<BottomAppBar>(R.id.bottomAppBar)
        val fabButton  = findViewById<FloatingActionButton>(R.id.openImage)
        fabButton.setOnClickListener {
            val currentItem = Adatper.peek(viewpager2.currentItem)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(currentItem?.uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }


        bottomBar.setOnMenuItemClickListener { item->
            val imageUri = Adatper.peek(viewpager2.currentItem)?.uri
            when(item.itemId){
                R.id.share->{
                    imageUri?.let{  shareImage(it)}
                    true
                }
                else -> false
            }

        }
    }
    fun shareImage(imageUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share image"))
    }
    private fun setupPadding(){
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = systemBars.top
            view.layoutParams = params

            insets
        }
    }
}