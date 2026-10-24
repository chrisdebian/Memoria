package com.youme.memoria.imageViewer.Album

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.youme.memoria.R
import com.youme.memoria.roomCach.AlbumEntity
import com.youme.memoria.roomCach.AlbumsList

class ListAlbumAdapter(
    private val onClick : (Boolean, String) -> Unit
) : RecyclerView.Adapter<ListAlbumAdapter.MyViewHolder>() {

    private var items  : List<AlbumsList> = emptyList()

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textView8)
        val imageViewList = listOf(R.id.imageView8,R.id.imageView9,R.id.imageView10,R.id.imageView11,)
        val box: MaterialCardView = itemView.findViewById(R.id.cardView9)
        val addButton : MaterialButton = itemView.findViewById<MaterialButton>(R.id.button2)


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.add_to_album_item, parent, false)

        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val albumItem = items[position]
        holder.title.text  = "${albumItem.name} (${albumItem.size})"
        albumItem.images.forEachIndexed { i ,item->
            val imageview= holder.itemView.findViewById<ImageView>(holder.imageViewList[i])
            Glide.with(holder.itemView.context)
                .load(item)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(imageview)
        }
        holder.addButton.apply{
            text =  if (albumItem.containsImage) "Remove" else "Add"
            icon  =  if (albumItem.containsImage) resources.getDrawable(R.drawable.baseline_delete_24) else resources.getDrawable(R.drawable.baseline_add_24)
        }

        holder.addButton.setOnClickListener {
            onClick(albumItem.containsImage,albumItem.albumId)
        }
    }

    override fun onViewRecycled(holder: MyViewHolder) {
        super.onViewRecycled(holder)
        holder.imageViewList.forEach { item->
            val imageview = holder.itemView.findViewById<ImageView>(item)
            Glide.with(holder.itemView.context).clear(imageview)
        }
    }
    fun submitData(albums : List<AlbumsList>){
        items = albums
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return items.size
    }
}