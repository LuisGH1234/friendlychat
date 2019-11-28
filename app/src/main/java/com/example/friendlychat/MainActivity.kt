package com.example.friendlychat

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.SnapshotParser
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.internal.FederatedSignInActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import de.hdodenhof.circleimageview.CircleImageView

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {
    // private lateinit var mFirebaseAnalytics: FirebaseAnalytics
    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        internal var messageTextView = itemView.findViewById(R.id.messageTextView) as TextView
        internal var messageImageView = itemView.findViewById(R.id.messageImageView) as ImageView
        internal var messengerTextView = itemView.findViewById(R.id.messengerTextView) as TextView
        internal var messengerImageView = itemView.findViewById(R.id.messengerImageView) as CircleImageView
    }

    companion object {
        const val TAG = "MainActivity"
        const val ANONYMOUS = "anonymous"
        const val MESSAGES_CHILD = "messages"
        private const val REQUEST_IMAGE = 2
        private const val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
    }

    private lateinit var mSendButton: Button
    private lateinit var mMessageRecyclerView: RecyclerView
    private lateinit var mLinearLayoutManager: LinearLayoutManager
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mMessageEditText: EditText
    private lateinit var mAddMessageImageView: ImageView

    private lateinit var mSharedPreferences: SharedPreferences

    private lateinit var mFirebaseAdapter: FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>
    private lateinit var mFirebaseDatabaseReference: DatabaseReference
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var mFirebaseAuth: FirebaseAuth
    private var mFirebaseUser: FirebaseUser? = null

    private var mUsername: String? = null
    private var mPhotoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mUsername = ANONYMOUS
        initializeFirebaseAuth()
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
            .addApi(Auth.GOOGLE_SIGN_IN_API)
            .build()

        // Initialize ProgressBar and RecyclerView.
        mProgressBar = findViewById(R.id.progressBar)
        mMessageRecyclerView = findViewById(R.id.messageRecyclerView)
        mLinearLayoutManager = LinearLayoutManager(this)
        mLinearLayoutManager.stackFromEnd = true
        mMessageRecyclerView.layoutManager = this.mLinearLayoutManager

        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().reference
        val parser = SnapshotParser<FriendlyMessage> { snapshot ->
                val friendlyMessage =
                    snapshot.getValue<FriendlyMessage>(FriendlyMessage::class.java)
                if (friendlyMessage != null) {
                    friendlyMessage.id = snapshot.key
                }
                return@SnapshotParser friendlyMessage!!
        }

        val messagesRef = mFirebaseDatabaseReference.child(MESSAGES_CHILD)
        val options = FirebaseRecyclerOptions.Builder<FriendlyMessage>()
            .setQuery(messagesRef, parser)
            .build()
        mFirebaseAdapter = object : FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>(options) {
            override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): MessageViewHolder {
                val inflater = LayoutInflater.from(viewGroup.context)
                return MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false))
            }

            override fun onBindViewHolder(viewHolder: MessageViewHolder, position: Int, friendlyMessage: FriendlyMessage) {
                mProgressBar.visibility = ProgressBar.INVISIBLE
                if (friendlyMessage.text != null) {
                    viewHolder.messageTextView.text = friendlyMessage.text
                    viewHolder.messageTextView.visibility = TextView.VISIBLE
                    viewHolder.messageImageView.visibility = ImageView.GONE
                }
                else if (friendlyMessage.imageUrl != null) {
                    val imageUrl = friendlyMessage.imageUrl
                    if (imageUrl!!.startsWith("gs://")) {
                        val storageReference = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(imageUrl)
                        storageReference.downloadUrl.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val downloadUrl = task.result!!.toString()
                                Glide.with(viewHolder.messageImageView.context)
                                    .load(downloadUrl)
                                    .into(viewHolder.messageImageView)
                            } else {
                                Log.w(TAG, "Getting download url was not successful.",
                                    task.exception
                                )
                            }
                        }
                    } else {
                        Glide.with(viewHolder.messageImageView.context)
                            .load(friendlyMessage.imageUrl)
                            .into(viewHolder.messageImageView)
                    }
                    viewHolder.messageImageView.visibility = ImageView.VISIBLE
                    viewHolder.messageTextView.visibility = TextView.GONE
                }
                viewHolder.messengerTextView.text = friendlyMessage.name
                if (friendlyMessage.photoUrl == null) {
                    viewHolder.messengerImageView.setImageDrawable(
                        ContextCompat.getDrawable(this@MainActivity,
                        R.drawable.ic_account_circle_black_36dp))
                } else {
                    Glide.with(this@MainActivity)
                    .load(friendlyMessage.photoUrl)
                    .into(viewHolder.messengerImageView)
                }
            }
        }

        mFirebaseAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val friendlyMessageCount = mFirebaseAdapter.itemCount
                val lastVisiblePosition =
                    mLinearLayoutManager.findLastCompletelyVisibleItemPosition()
                // If the recycler view is initially being loaded or the
                // user is at the bottom of the list, scroll to the bottom
                // of the list to show the newly added message.
                if (lastVisiblePosition == -1 || positionStart >= friendlyMessageCount - 1
                        && lastVisiblePosition == positionStart - 1) {
                    mMessageRecyclerView.scrollToPosition(positionStart)
                }
            }
        })

        mMessageRecyclerView.adapter = mFirebaseAdapter
        mMessageEditText = findViewById(R.id.messageEditText)

        mMessageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                mSendButton.isEnabled = charSequence.toString().trim().length > 0
            }

            override fun afterTextChanged(editable: Editable) {}
        })

        mSendButton = findViewById(R.id.sendButton)
        mSendButton.setOnClickListener {
            val friendlyMessage = FriendlyMessage(
                mMessageEditText.text.toString(),
                mUsername,
                mPhotoUrl,
                null /* no image */
            )
            mFirebaseDatabaseReference.child(MESSAGES_CHILD)
                .push().setValue(friendlyMessage)
            mMessageEditText.setText("")
        }
        // Helpers.hideSoftKeyboard(this)

        mAddMessageImageView = findViewById(R.id.addMessageImageView)
        mAddMessageImageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }
        /*fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }*/
    }

    private fun initializeFirebaseAuth() {
        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseUser = mFirebaseAuth.currentUser

        if (mFirebaseUser == null) {
            // Not signed in, launch the Sign In activity
            Log.d(TAG, "User Signed In")
            startActivity(Intent(this, SignInActivity::class.java))
            return finish()
        } else {
            Log.d(TAG, "User NOT Signed In")
            mUsername = mFirebaseUser?.displayName
            if (mFirebaseUser?.photoUrl != null)
                mPhotoUrl = mFirebaseUser?.photoUrl.toString()
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in.
    }

    override fun onPause() {
        mFirebaseAdapter.stopListening()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAdapter.startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                mFirebaseAuth.signOut()
                Auth.GoogleSignInApi.signOut(mGoogleApiClient)
                mUsername = ANONYMOUS
                startActivity(Intent(this, SignInActivity::class.java))
                finish()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:$connectionResult")
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    val uri = data.data
                    Log.d(TAG, "Uri: " + uri!!.toString())

                    val tempMessage = FriendlyMessage(null, mUsername, mPhotoUrl, LOADING_IMAGE_URL)
                    mFirebaseDatabaseReference.child(MESSAGES_CHILD).push()
                    .setValue(tempMessage) { databaseError, databaseReference ->
                        if (databaseError == null) {
                            val key = databaseReference.key
                            val storageReference = FirebaseStorage.getInstance()
                                .getReference(mFirebaseUser!!.uid)
                                .child(key!!)
                                .child(uri.lastPathSegment!!)

                            putImageInStorage(storageReference, uri, key)
                        } else {
                            Log.w(TAG, "Unable to write message to database.",
                                databaseError.toException())
                        }
                    }
                }
            }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri, key: String) {
        storageReference.putFile(uri).addOnCompleteListener(this@MainActivity)
        { task ->
            if (task.isSuccessful) {
                task.result!!.metadata!!.reference!!.downloadUrl
                    .addOnCompleteListener(this@MainActivity
                    ) { task ->
                        if (task.isSuccessful) {
                            val friendlyMessage = FriendlyMessage(null, mUsername, mPhotoUrl,
                                task.result!!.toString())
                            mFirebaseDatabaseReference.child(MESSAGES_CHILD).child(key)
                                .setValue(friendlyMessage)
                        }
                    }
            } else {
                Log.w(TAG, "Image upload task was not successful.",
                    task.exception
                )
            }
        }
    }

    private fun logMessageSent() {
        // Log message has been sent.
        FirebaseAnalytics.getInstance(this).logEvent("message", null)
    }
}
