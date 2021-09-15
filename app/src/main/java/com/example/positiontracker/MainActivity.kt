package com.example.positiontracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.location.LocationListener
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.room.*
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContentProviderCompat.requireContext
import java.lang.Exception
import java.nio.file.FileStore
import kotlin.concurrent.thread
import android.content.DialogInterface


class MainActivity : AppCompatActivity() {
    private var locationManager: LocationManager? = null

    private val CREATE_CODE = 40

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomButton = findViewById<Button>(R.id.bottomButton)
        bottomButton.text = "Start"
        val exportButton = findViewById<Button>(R.id.export)
        exportButton.text = "Export"
        val clearButton = findViewById<Button>(R.id.clear)
        clearButton.text = "Clear data"


        val text: TextView = findViewById<TextView>(R.id.textView)

        text.text = "Unknown run state"

        var running = false

        bottomButton.setOnClickListener {


            if (running) {
                sendCommandToService("ACTION_STOP_SERVICE")
                text.text = "Stopped"
                running = false
                bottomButton.text = "Start"
            } else {
                sendCommandToService("ACTION_START_OR_RESUME_SERVICE")
                println("Started in Main")
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
                    )
                }
                running = true

                bottomButton.text = "Stop"

                text.text = "Running"
            }


        }

        exportButton.setOnClickListener {
            exportButton.isEnabled = false
            println("Clicked!")

            Thread(Runnable {

                try {
                    /*val fileOut = openFileOutput(LocalDateTime.now().toString() + ".gpx", MODE_WORLD_READABLE)
                    val outputWriter = OutputStreamWriter(fileOut)
                    outputWriter.write(exportString)
                    outputWriter.close()*/

                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.type = "application/gpx+xml"
                    intent.putExtra(Intent.EXTRA_TITLE, LocalDateTime.now().toString() + ".gpx")
                    startActivityForResult(intent, CREATE_CODE)


                } catch (e: Exception) {
                    e.printStackTrace()
                }


            }).start()
        }

        val db = Room.databaseBuilder(
            applicationContext,
            MainActivity.AppDatabase::class.java, "positionsList"
        ).build()

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)

        builder.setTitle("Confirm")
        builder.setMessage("Are you sure you want to clear all gps data that is not exported?")

        builder.setNegativeButton(
            "NO",
            DialogInterface.OnClickListener { dialog, which -> // Do nothing
                dialog.dismiss()
                runOnUiThread {
                    Toast.makeText(
                        baseContext, "Cancelled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        builder.setPositiveButton(
            "YES",
            DialogInterface.OnClickListener { dialog, which -> // Clear data

                runOnUiThread {
                    Thread(Runnable {
                        val posDao = db.positionDao()

                        posDao.clear()
                        println("Data cleared")
                        runOnUiThread {
                            dialog.dismiss()
                            Toast.makeText(
                                baseContext, "Data cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }).start()


                }
            })


        val alert: AlertDialog = builder.create()

        clearButton.setOnClickListener {
            runOnUiThread {
                alert.show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Thread(Runnable {
            var exportString =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n" +
                        "\n" +
                        "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\">\n" +
                        "  <metadata>\n" +
                        "    <link href=\"https://marksism.space\">\n" +
                        "      <text>Position tracker</text>\n" +
                        "    </link>\n" +
                        "  </metadata>\n" +
                        "  <trk>\n" +
                        "    <name>Example GPX Document</name>\n" +
                        "    <trkseg>"

            val db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java, "positionsList"
            ).build()

            db.positionDao().getAll().forEach {
                exportString += "<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\">\n" +
                        "        <time>${it.time}</time>\n" +
                        "      </trkpt>"
            }

            exportString += "</trkseg>\n" +
                    "  </trk>\n" +
                    "</gpx>"

            if (resultCode == Activity.RESULT_OK) {
                println("Ok file save")

                if (data != null) {
                    val fileUrl: Uri = data.data!!

                    val parcel = this.contentResolver.openFileDescriptor(fileUrl, "w")

                    val fileOut = FileOutputStream(parcel?.fileDescriptor)

                    fileOut.write(exportString.toByteArray())

                    fileOut.close()

                    parcel?.close()

                    //display file saved message
                    runOnUiThread {
                        Toast.makeText(
                            baseContext, "File saved successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        val exportButton = findViewById<Button>(R.id.export)
                        exportButton.isEnabled = true
                    }
                }


            } else {
                runOnUiThread {
                    Toast.makeText(
                        baseContext, "Unable to save file",
                        Toast.LENGTH_SHORT
                    ).show()
                    val exportButton = findViewById<Button>(R.id.export)
                    exportButton.isEnabled = true
                }
            }
        }).start()


    }

    @Entity
    data class Position(
        @PrimaryKey val time: String,
        @ColumnInfo(name = "lat") val latitude: Double,
        @ColumnInfo(name = "long") val longitude: Double
    )

    @Dao
    interface PositionDao {
        @Query("SELECT * FROM position")
        fun getAll(): List<Position>

        @Insert
        fun insertAll(vararg positions: Position)

        @Delete
        fun delete(position: Position)

        @Query("SELECT COUNT(lat) FROM position")
        fun getCount(): Int

        @Query("DELETE FROM [position]")
        fun clear()
    }

    @Database(entities = arrayOf(Position::class), version = 1)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun positionDao(): PositionDao
    }

    private fun sendCommandToService(action: String) =
        Intent(this, posTrackService::class.java).also {
            it.action = action
            this.startService(it)
        }

}