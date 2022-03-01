package com.lahsuak.apps.locationtracker

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.lahsuak.apps.locationtracker.databinding.ContactItemBinding
import com.lahsuak.apps.locationtracker.model.User


class ContactAdapter(
    private val context: Context,
    private var list: List<User>,
    private val listener: ContactListener
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {
    //ListAdapter<User, ContactAdapter.ContactViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ContactItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val currentItem = list[position]
        holder.bind(currentItem)
    }

    inner class ContactViewHolder(private val binding: ContactItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                listener.onItemClicked(list[pos], pos)
            }
        }

        fun bind(user: User) {
            binding.txtNumber.text = user.phoneNumber
            binding.txtName.text = user.name+"\n"+LatLng(user.lat,user.lng).toString()
        }

    }

    interface ContactListener {
        fun onItemClicked(user: User, position: Int)
    }

    //    class DiffCallback : DiffUtil.ItemCallback<User>() {
//        override fun areItemsTheSame(oldItem: User, newItem: User) =
//            oldItem.uid == newItem.uid
//
//        @SuppressLint("DiffUtilEquals")
//        override fun areContentsTheSame(oldItem: User, newItem: User) =
//            oldItem == newItem
//    }
//    fun updateList(newList: ArrayList<User>) {
//        list.clear()
//        list.addAll(newList)
//        notifyDataSetChanged()
//    }

    override fun getItemCount() = list.size
}