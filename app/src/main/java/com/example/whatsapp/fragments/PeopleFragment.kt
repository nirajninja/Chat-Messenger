package com.example.whatsapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsapp.*
import com.example.whatsapp.modals.User
import com.firebase.ui.firestore.paging.FirestorePagingAdapter
import com.firebase.ui.firestore.paging.FirestorePagingOptions
import com.firebase.ui.firestore.paging.LoadingState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.android.synthetic.main.activity_chats_fragment.*
import java.lang.Exception


private const val DELETED_VIEW_TYPE=1
private const val NORMAL_VIEW_TYPE=2

class PeopleFragment : Fragment() {
    lateinit var mAdapter:FirestorePagingAdapter<User,RecyclerView.ViewHolder>
    val auth by lazy{
        FirebaseAuth.getInstance()

    }
    val  database by lazy{
        FirebaseFirestore.getInstance().collection("users")
            .orderBy("name",Query.Direction.ASCENDING)

    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setupAdaper()
    return inflater.inflate(R.layout.activity_chats_fragment,container,false)
    }

    private fun setupAdaper() {
        val config=PagedList.Config.Builder()
            .setPrefetchDistance(2)
            .setPageSize(10)
            .setEnablePlaceholders(false)
            .build()
        val options=FirestorePagingOptions.Builder<User>()
            .setLifecycleOwner(viewLifecycleOwner)
            .setQuery(database,config, User::class.java)
            .build()
        mAdapter=object:FirestorePagingAdapter<User,RecyclerView.ViewHolder>(options){

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):RecyclerView.ViewHolder {
                return when (viewType) {
                    NORMAL_VIEW_TYPE -> UsersViewHolder(
                        layoutInflater.inflate(
                            R.layout.list_item,
                            parent,
                            false
                        )
                    )
                    else -> EmptyViewHolder(
                        layoutInflater.inflate(
                            R.layout.empty_view,
                            parent,
                            false
                        )
                    )
                }
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, model: User) {
                    if(holder is UsersViewHolder){
                        holder.bind(user=model) { name: String, photo: String, id: String ->
                            val intent= Intent(requireContext(),
                                ChatActivity::class.java)
                            intent.putExtra(UID,id)
                            intent.putExtra(NAME,name)
                            intent.putExtra(IMAGE,photo)
                            startActivity(intent)
                        }

                    }else{
                        //
                    }
            }

            override fun onLoadingStateChanged(state: LoadingState) {
                super.onLoadingStateChanged(state)
                when(state){
                    LoadingState.ERROR->{ }
                    LoadingState.LOADING_INITIAL->{ }
                    LoadingState.LOADING_MORE->{ }
                    LoadingState.FINISHED->{ }
                    LoadingState.LOADED->{ }

                }
            }

            override fun onError(e: Exception) {
                super.onError(e)
            }

            override fun getItemViewType(position: Int): Int {

                val item=getItem(position)?.toObject(User::class.java)
                return  if(auth.uid==item!!.uid)
                {
                    DELETED_VIEW_TYPE
                }else
                {
                    NORMAL_VIEW_TYPE
                }

            }

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.apply{
            layoutManager=LinearLayoutManager(requireContext())
            adapter=mAdapter
        }
    }

}
