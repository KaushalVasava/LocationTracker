package com.lahsuak.apps.locationtracker.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lahsuak.apps.locationtracker.ContactAdapter
import com.lahsuak.apps.locationtracker.R
import com.lahsuak.apps.locationtracker.databinding.FragmentHomeBinding
import com.lahsuak.apps.locationtracker.model.User


class HomeFragment : Fragment(R.layout.fragment_home), ContactAdapter.ContactListener {

    private lateinit var binding: FragmentHomeBinding
    private var list = mutableListOf<User>()
    private lateinit var contactAdapter: ContactAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentHomeBinding.bind(view)
        contactAdapter = ContactAdapter(
            requireContext(),
            list,
            object : ContactAdapter.ContactListener {
                override fun onItemClicked(user: User, position: Int) {

                }
            })

        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.setHasFixedSize(false)
        val decoration = DividerItemDecoration(
            requireContext().applicationContext,
            DividerItemDecoration.VERTICAL
        )
        binding.rvContacts.addItemDecoration(decoration)
        binding.rvContacts.adapter = contactAdapter
        fetchData()
    }

    private fun fetchData() {
        val fireRef = FirebaseDatabase.getInstance().reference.child("LTUsers")
        fireRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                list.clear()
                for (sp in snapshot.children) {
                    val user = sp.getValue(User::class.java)
                    if (user != null) {
                        Log.d("TAG", "onDataChange: ${user.name}")
                        list.add(user)
                    }
                }
                contactAdapter = ContactAdapter(requireContext(), list, this@HomeFragment)
                binding.rvContacts.adapter = contactAdapter
                contactAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })

    }


    override fun onItemClicked(user: User, position: Int) {
        val action = HomeFragmentDirections.actionHomeFragmentToTrackingFragment(user.uid)
        findNavController().navigate(action)
    }
}