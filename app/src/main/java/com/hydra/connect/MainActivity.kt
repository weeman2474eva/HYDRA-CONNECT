package com.hydra.connect

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
 private val client=OkHttpClient()
 private lateinit var root:LinearLayout
 private var token=""
 override fun onCreate(state:Bundle?){super.onCreate(state);token=getSharedPreferences("hc",Context.MODE_PRIVATE).getString("token","")?:"";if(token.isBlank()) loginScreen() else home()}
 private fun screen(){val scroll=ScrollView(this);root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(36,36,36,36);root.setBackgroundColor(Color.rgb(7,14,18));scroll.addView(root);setContentView(scroll)}
 private fun text(value:String,size:Float=17f)=TextView(this).apply{text=value;textSize=size;setTextColor(Color.WHITE);setPadding(8,10,8,10)}
 private fun input(hintText:String,secret:Boolean=false)=EditText(this).apply{hint=hintText;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setPadding(18,18,18,18);setBackgroundColor(Color.rgb(18,31,36));if(secret)inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
 private fun button(label:String)=Button(this).apply{text=label;isAllCaps=false;isFocusable=true;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(16,92,88))}
 private fun loginScreen(){screen();root.addView(text("HYDRA CONNECT",30f));root.addView(text("Your service. Your support. One place.",16f));val u=input("IPTV username");val p=input("Password",true);val b=button("Sign in");val status=text("",14f);root.addView(u);root.addView(p);root.addView(b);root.addView(status);b.setOnClickListener{status.text="Signing in...";post("login.php",JSONObject().put("username",u.text.toString()).put("password",p.text.toString()),false){j->if(j.optBoolean("ok")){token=j.optString("token");getSharedPreferences("hc",Context.MODE_PRIVATE).edit().putString("token",token).apply();home()}else status.text=j.optString("error","Login failed")}}}
 private fun home(){get("portal.php"){j->if(!j.optBoolean("ok")){logout();return@get};screen();val settings=j.optJSONObject("settings")?:JSONObject();val customer=j.optJSONObject("customer")?:JSONObject();root.addView(text(settings.optString("brand_name","HYDRA CONNECT"),28f));root.addView(text(settings.optString("welcome","Everything you need, in one place."),16f));root.addView(text("MY SERVICE\n"+customer.optString("username")+"\nExpiry: "+customer.optString("expiry_at","Not available"),19f));addCard("News & Updates","Latest announcements");addCard("Messages","Contact your seller");addCard("Report a Problem","Send a support report");addCard("Service Status",settings.optString("service_status","All systems operational"));addCard("Apps","Recommended apps");addCard("Renew Service",settings.optString("renew_message","Send a renewal request"));val out=button("Sign out");root.addView(out);out.setOnClickListener{logout()}}}
 private fun addCard(title:String,sub:String){val b=button(title+"\n"+sub);b.setPadding(20,16,20,16);root.addView(b)}
 private fun logout(){getSharedPreferences("hc",Context.MODE_PRIVATE).edit().clear().apply();token="";loginScreen()}
 private fun get(ep:String,done:(JSONObject)->Unit){val r=Request.Builder().url(Config.API_BASE_URL+ep);if(token.isNotBlank())r.header("Authorization","Bearer "+token);client.newCall(r.build()).enqueue(object:Callback{override fun onFailure(call:Call,e:IOException){runOnUiThread{Toast.makeText(this@MainActivity,"Unable to connect",Toast.LENGTH_LONG).show()}};override fun onResponse(call:Call,response:Response){val raw=response.body?.string()?:"{}";val j=try{JSONObject(raw)}catch(e:Exception){JSONObject()};runOnUiThread{done(j)}}})}
 private fun post(ep:String,j:JSONObject,auth:Boolean,done:(JSONObject)->Unit){val media="application/json".toMediaTypeOrNull();val body=j.toString().toRequestBody(media);val r=Request.Builder().url(Config.API_BASE_URL+ep).post(body);if(auth)r.header("Authorization","Bearer "+token);client.newCall(r.build()).enqueue(object:Callback{override fun onFailure(call:Call,e:IOException){runOnUiThread{Toast.makeText(this@MainActivity,"Unable to connect",Toast.LENGTH_LONG).show()}};override fun onResponse(call:Call,response:Response){val raw=response.body?.string()?:"{}";val data=try{JSONObject(raw)}catch(e:Exception){JSONObject()};runOnUiThread{done(data)}}})}
}