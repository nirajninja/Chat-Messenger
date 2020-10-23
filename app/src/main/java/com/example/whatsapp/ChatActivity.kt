package com.example.whatsapp

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsapp.Adapters.ChatAdapter
import com.example.whatsapp.Utils.KeyboardVisibilityUtil
import com.example.whatsapp.Utils.isSameDayAs
import com.example.whatsapp.modals.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers

import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.google.GoogleEmojiProvider
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


const val  UID="uid"
const val NAME="name"
const val IMAGE="photo"

class ChatActivity : AppCompatActivity() {
          private val friendID:String by lazy{
              intent.getStringExtra(UID)
          }
    private val name:String by lazy{
        intent.getStringExtra(NAME)
    }
    private val image:String by lazy{
        intent.getStringExtra(IMAGE)
    }
    private val mCurrentUid:String by lazy{
        FirebaseAuth.getInstance().uid!!
    }
    private val db:FirebaseDatabase by lazy{
        FirebaseDatabase.getInstance()
    }

    lateinit var currentUser: User
    private lateinit var keyboardVisibilityHelper: KeyboardVisibilityUtil

    private val messages= mutableListOf<ChatEvent>()
  lateinit var   chatAdapter: ChatAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EmojiManager.install(GoogleEmojiProvider())

        setContentView(R.layout.activity_chat)
        //change 1-
        keyboardVisibilityHelper=KeyboardVisibilityUtil(rootView){
            msgRv.scrollToPosition(messages.size-1)
        }
        //

        FirebaseFirestore.getInstance().collection("users").document(mCurrentUid).get()
            .addOnSuccessListener {
              currentUser=it.toObject(User::class.java)!!



            }
        chatAdapter= ChatAdapter(messages,mCurrentUid)
        msgRv.apply {
            layoutManager=LinearLayoutManager(this@ChatActivity)
            adapter=chatAdapter
        }
        nameTv.text=name
        Picasso.get().load(image).into(userImgView)

        val emojiPopup= EmojiPopup.Builder.fromRootView(rootView).build(msgEdtv)
        smileBtn.setOnClickListener {
            emojiPopup.toggle()
        }
        swipeToLoad.setOnRefreshListener {
            val workerScope= CoroutineScope(Dispatchers.Main)
            workerScope.launch{
                delay(2000)
                swipeToLoad.isRefreshing=false
            }
        }
        sendBtn.setOnClickListener {
            msgEdtv.text?.let{
                if(it.isNotEmpty()){
                    sendMessage(it.toString())
                    it.clear()
                }
            }
        }
        listenToMessages() { msg, update ->
            if (update) {
                updateMessage(msg)
            } else {
                addMessage(msg)
            }
        }

        chatAdapter.highFiveClick = { id, status ->
            updateHighFive(id, status)
        }
        updateReadCount()

    }


    private fun updateHighFive(id:String,status:Boolean){
        getMessages(friendID).child(id).updateChildren(mapOf("Liked" to status))
    }
    private fun updateReadCount() {
        getInbox(mCurrentUid,friendID).child("count").setValue(0)

    }

    private fun sendMessage(msg: String) {

        val id=getMessages(friendID).push().key
        checkNotNull(id){"Cannot be null"}

        val msgMap= Message(msg,mCurrentUid,id)
        getMessages(friendID).child(id).setValue(msgMap).addOnSuccessListener {
            Log.i("CHATS","completed")
        }.addOnFailureListener {
            Log.i("CHATS",it.localizedMessage)
        }
        updateLastMessage(msgMap)


    }

    private fun updateLastMessage(message: Message) {

        val inboxMap =Inbox(
            message.msg,
            friendID,
            name,
            image,
            message.sentAt,
            count=0
        )

        getInbox(mCurrentUid,friendID).setValue(inboxMap).addOnSuccessListener {

            getInbox(friendID,mCurrentUid).addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onCancelled(p0: DatabaseError) {

                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    val value=snapshot.getValue(Inbox::class.java)
                    inboxMap.apply {
                        from=message.senderId
                        name=currentUser.name
                        image=currentUser.thumbImage
                        count=1

                    }
                    value?.let{
                        if(it.from==message.senderId){
                            inboxMap.count=value.count+1
                        }
                    }
                    getInbox(friendID,mCurrentUid).setValue(inboxMap)
                }

            })
        }
    }

    private fun listenToMessages(newMsg: (msg: Message, update: Boolean) -> Unit){
        getMessages(friendID)
            .orderByKey()
            .addChildEventListener(object :ChildEventListener{
                override fun onCancelled(p0: DatabaseError) {

                }

                override fun onChildMoved(p0: DataSnapshot, p1: String?) {

                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {

                }

                override fun onChildAdded(snapshot: DataSnapshot, p1: String?) {

                    val msg=snapshot.getValue(Message::class.java)!!
                    addMessage(msg)

                }

                override fun onChildRemoved(p0: DataSnapshot) {

                }

            })
    }

    private fun addMessage(msg: Message) {

        val eventBefore=messages.lastOrNull()

        if((eventBefore!=null && !eventBefore.sentAt.isSameDayAs(msg.sentAt) )|| eventBefore==null)
        {
            messages.add(
                dateHeader(
                    msg.sentAt,context=this
                )
            )

        }
        messages.add(msg)

        chatAdapter.notifyItemInserted(messages.size-1)
        msgRv.scrollToPosition(messages.size-1)



    }

    private fun marksAsRead(){
        getInbox(friendID,mCurrentUid).child("count").setValue(0)
    }

    private fun getMessages(friendId: String)=db.reference.child("messages/${getId(friendId)}")

    private fun getInbox(toUser:String,fromUser:String)=
        db.reference.child("chats/$toUser/$fromUser")


    private fun getId(friendId:String):String
    { //id for the messages
        return if(friendId>mCurrentUid){
            mCurrentUid+friendId
        }else{
            friendId+mCurrentUid
        }


    }
    private fun updateMessage(msg: Message) {
        val position = messages.indexOfFirst {
            when (it) {
                is Message -> it.msgId == msg.msgId
                else -> false
            }
        }
        messages[position] = msg

        chatAdapter.notifyItemChanged(position)
    }

    override fun onResume() {
        super.onResume()
        rootView.viewTreeObserver
            .addOnGlobalLayoutListener(keyboardVisibilityHelper.visibilityListener)
    }

    override fun onPause() {
        super.onPause()
        rootView.viewTreeObserver
            .removeOnGlobalLayoutListener(keyboardVisibilityHelper.visibilityListener)
    }

    companion object {

        fun createChatActivity(context: Context, id: String, name: String, image: String): Intent {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra(UID, id)
            intent.putExtra(NAME, name)
            intent.putExtra(IMAGE, image)

            return intent
        }
    }

}