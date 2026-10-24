package com.youme.memoria.imageViewer.Album

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.youme.memoria.Album.AlbumViewModel
import com.youme.memoria.Album.CreateAlbumBottomFrag
import com.youme.memoria.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AddToAlbumBottomSheet(private val uri : String) : BottomSheetDialogFragment(R.layout.add_to_album_bottom_freg) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet
    private val albumViewModel : AlbumViewModel by activityViewModels()
    private lateinit var Adapter : ListAlbumAdapter

    @SuppressLint("StringFormatInvalid")
    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet =
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return
        bottomSheet.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = Resources.getSystem().displayMetrics.heightPixels
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            behavior.skipCollapsed = true
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycleView()
        setupUi()
    }

    private fun setupUi(){
        val createAlbum = requireView().findViewById<View>(R.id.createAlbum)
        createAlbum.setOnClickListener {
            CreateAlbumBottomFrag().show(parentFragmentManager,"")
        }
    }

    private fun setupRecycleView(){
        Adapter = ListAlbumAdapter(){toRemove,albumId->
            Log.d("testDataset", "onViewCreated: $toRemove $albumId $uri")
            if (toRemove) albumViewModel.deletePhotoFromAlbum(uri,albumId) else albumViewModel.addPhotoToAlbum(uri,albumId)

        }


        val recyclerView = requireView().findViewById<RecyclerView>(R.id.rec)

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL,false)
            adapter = Adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                albumViewModel.getAlbums(uri).collectLatest {data->
                    requireView().findViewById<View>(R.id.emptyState).isVisible=data.isEmpty()
                    Adapter.submitData(data)

                }
            }

        }
    }
}