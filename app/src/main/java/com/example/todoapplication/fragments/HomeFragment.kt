package com.example.todoapplication.fragments

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todoapplication.MapsActivity
import com.example.todoapplication.R
import com.example.todoapplication.databinding.FragmentHomeBinding
import com.example.todoapplication.utils.Task
import com.example.todoapplication.utils.ToDoAdapter
import com.example.todoapplication.utils.ToDoData
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class HomeFragment : Fragment(), AddTodoPopupFragment.DialogNextButtonClickListener,
    ToDoAdapter.ToDoAdapterClicksInterface {

    private lateinit var auth: FirebaseAuth
    private lateinit var databaseRef: DatabaseReference
    private lateinit var navController: NavController
    private lateinit var binding : FragmentHomeBinding
    private var popupFragment: AddTodoPopupFragment? = null
    private lateinit var adapter : ToDoAdapter
    private lateinit var mList : MutableList<ToDoData>
    val CHANNEL_ID = "channelID"
    val CHANNEL_NAME = "channelName"
    private lateinit var manager: NotificationManager
    val NOTIFICATION_ID = 0
    private var index = 0
    var map: HashMap<ToDoData, Int> = HashMap()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        setHasOptionsMenu(true)
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        binding.gpsBtn.setOnClickListener{
            val intent = Intent(context, MapsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        init(view)
        getDataFromFirebase()
        registerEvents()
        loadData()
        binding.btButton.setOnClickListener {
            saveData()
        }
    }

    private fun saveData() {
        val insertedText : String = binding.etText.text.toString()
        binding.tvText.text = insertedText

        val sharedPreferences : SharedPreferences = requireContext().getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val editor : SharedPreferences.Editor = sharedPreferences.edit()
        editor.apply{
            putString("STRING_KEY", insertedText)
        }.apply()
        Toast.makeText(context, "Data saved", Toast.LENGTH_SHORT).show()

        binding.etText.setText("")
    }

    private fun loadData() {
        val sharedPreferences : SharedPreferences = requireContext().getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val savedString : String? = sharedPreferences.getString("STRING_KEY", null)

        binding.tvText.text = savedString
    }

    private fun init(view : View) {
        navController = Navigation.findNavController(view)
        auth = FirebaseAuth.getInstance()
        databaseRef = FirebaseDatabase.getInstance().reference.child("Tasks")
            .child("User id: " + auth.currentUser?.uid.toString())

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        mList = mutableListOf()
        adapter = ToDoAdapter(mList)
        adapter.setListener(this)
        binding.recyclerView.adapter = adapter

        createNotificationChannel()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.logout){
            auth.signOut()
            navController.navigate(R.id.action_homeFragment_to_signInFragment)
            return true
        }
        return true
    }

    fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    lightColor = Color.RED
                    enableLights(true)
                }
            manager = requireContext().getSystemService<NotificationManager>()!!
            manager.createNotificationChannel(channel)
        }
    }

    private fun getDataFromFirebase() {
        databaseRef.addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                mList.clear()
                for(taskSnapshot in snapshot.children) {
                    val toDoData = taskSnapshot.key?.let {
                        ToDoData(it, taskSnapshot.getValue(Task::class.java)!!)
                    }
                    val task = Task(toDoData?.task!!.name, toDoData.task.date)
                    if(task != null) {
                        mList.add(toDoData!!)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun registerEvents() {
        binding.addBtnHome.setOnClickListener {
            if(popupFragment != null)
                childFragmentManager.beginTransaction().remove(popupFragment!!).commit()
            popupFragment = AddTodoPopupFragment()
            popupFragment!!.setListener(this)
            popupFragment!!.show(childFragmentManager, AddTodoPopupFragment.TAG)
        }
    }


    @SuppressLint("MissingPermission")
    override fun onSaveTask(name: String, todoEt: TextInputEditText, date : String, todoDate: TextInputEditText) {
        val task : Task = Task(name , date)

        databaseRef.push().setValue(task).addOnCompleteListener {
            if(it.isSuccessful) {
                val notification = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                    .setContentTitle("Task notification")
                    .setContentText("Task: '${name}' was successfully added!")
                    .setSmallIcon(R.drawable.baseline_android_24)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()

                val notificationManager = NotificationManagerCompat.from(requireContext())
                notificationManager.notify(NOTIFICATION_ID, notification)
            } else {
                Toast.makeText(context, it.exception?.message, Toast.LENGTH_SHORT).show()
            }
            todoEt.text = null
            todoDate.text = null
            popupFragment!!.dismiss()
        }
    }

    override fun onUpdateTask(todoData: ToDoData, todoEt: TextInputEditText, todoDate: TextInputEditText) {
        val map = HashMap<String, Any>()
        map[todoData.taskId] = todoData.task

        databaseRef.updateChildren(map).addOnCompleteListener {
            if(it.isSuccessful) {
                Toast.makeText(context, "Updated Successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, it.exception?.message, Toast.LENGTH_SHORT).show()
            }
            todoEt.text = null
            todoDate.text = null
            popupFragment!!.dismiss()
        }
    }

    override fun onDeleteTaskBtnClicked(toDoData: ToDoData) {
        databaseRef.child(toDoData.taskId).removeValue().addOnCompleteListener {
            if(it.isSuccessful) {
                Toast.makeText(context, "Deleted Successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, it.exception?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onEditTaskBtnClicked(toDoData: ToDoData) {
        if(popupFragment != null)
            childFragmentManager.beginTransaction().remove(popupFragment!!).commit()
        popupFragment = AddTodoPopupFragment.newInstance(toDoData.taskId, toDoData.task.name!!,
            toDoData.task.date!!
        )
        popupFragment!!.setListener(this)
        popupFragment!!.show(childFragmentManager, AddTodoPopupFragment.TAG)
    }
}

