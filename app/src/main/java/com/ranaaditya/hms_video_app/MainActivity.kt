package com.ranaaditya.hms_video_app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    var useratoken = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT > 9) {
            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
        }

        mainConnectButton.setOnClickListener { GenerateUserRoomToken() }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            val callIntent = Intent(this@MainActivity, SettingsActivity::class.java)
            callIntent.putExtra("from", "launchscreen")
            startActivity(callIntent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_video, menu)
        return true
    }

    fun GenerateUserRoomToken() {
        var resultString = ""

        if (mainRoomId.text.isEmpty()) {
            Toast.makeText(
                applicationContext,
                "Room id is a mandatory parameter",
                Toast.LENGTH_LONG
            ).show()
        }

        // create your json here
        val tokenJsonObject = JSONObject()

        try {

            tokenJsonObject.put("room_id", mainRoomId.text.toString())
            tokenJsonObject.put("user_name", mainUserName.text.toString())
            tokenJsonObject.put("role", "Guest")
            tokenJsonObject.put(
                "env",
                mainEndpoint.text.toString().split("\\.".toRegex()).toTypedArray()[0].replace(
                    "wss://",
                    ""
                )
            )

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        Log.v("HMSClient", tokenJsonObject.toString())

        val client = OkHttpClient()

        val JSON = MediaType.parse("application/json; charset=utf-8")

        val requestBody = RequestBody.create(JSON, tokenJsonObject.toString())

        val request: Request = Request.Builder()
            .url(BuildConfig.TOKEN_ENDPOINT)
            .post(requestBody)
            .build()

        var response: Response? = null
        var jsonObj: JSONObject? = null
        var res: String? = ""

        try {

            response = client.newCall(request).execute()
            resultString = response.body().string()

            jsonObj = JSONObject(resultString)

            Log.d("token", jsonObj.getString("token"));

            res = jsonObj.getString("token")

            val roomIntent = Intent(this@MainActivity, RoomActivity::class.java)

            roomIntent.putExtra("server", mainEndpoint.text.toString())
            roomIntent.putExtra("room", mainRoomId.text.toString())
            roomIntent.putExtra(
                "user",
                if (mainUserName.text.toString().isEmpty()) "rana" else mainUserName.getText()
                    .toString()
            )
            roomIntent.putExtra("auth_token", res)
            roomIntent.putExtra("env", "others")

            startActivity(roomIntent)

        } catch (e: Exception) {

            Toast.makeText(applicationContext, "Error in receiving token", Toast.LENGTH_LONG).show()
            e.printStackTrace()

        }
    }
}