package com.example.dhivak.newsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<String> newsStories = new ArrayList<>();
    ArrayList<String> content = new ArrayList<>();
    ArrayAdapter<String> arrayAdapter;

    SQLiteDatabase articlesDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        articlesDB = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);
        articlesDB.execSQL("CREATE TABLE IF NOT EXISTS articles (id INTEGER PRIMARY KEY, articleId INTEGER, title VARCHAR, content VARCHAR)");

        downloadTask task = new downloadTask();
        try{
           // task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");

        }catch (Exception e){
            e.printStackTrace();
        }

        //Sets up arrayAdapter
        listView = findViewById(R.id.listView);
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, newsStories);
        listView.setAdapter(arrayAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent intent = new Intent(MainActivity.this, ArticleActivity.class);
                intent.putExtra("content", content.get(i));
                startActivity(intent);
            }
        });

        updateListView();

    }

    public void updateListView(){
        Cursor c = articlesDB.rawQuery("SELECT * FROM articles", null);

        int contentIndex = c.getColumnIndex("content");
        int titleIndex = c.getColumnIndex("title");

        if(c.moveToFirst()){
            newsStories.clear();
            content.clear();

            do {

                newsStories.add(c.getString(titleIndex));
                content.add(c.getString(contentIndex));

            } while (c.moveToNext());

            arrayAdapter.notifyDataSetChanged();
        }
    }


    public class downloadTask extends AsyncTask<String, Void, String>{

        /*
            This class works in the background to handle the data that needs to be downloaded from our API

         */
        @Override
        protected String doInBackground(String... urls) {
            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            try{
                url = new URL(urls[0]); //Creates a new url at index 0
                urlConnection = (HttpURLConnection) url.openConnection(); //Establishes connection and opens URL

                InputStream inputStream = urlConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                //Lets us parse through everything
                int data = inputStreamReader.read();

                while(data != -1){
                    char current = (char) data;
                    result += current;
                    data = inputStreamReader.read();
                }

                //We use a JSONArray bc it allows us to manipulate data/variables easier
                JSONArray jsonArray = new JSONArray(result);

                int numOfItems = 20;

                if(jsonArray.length() < 20){
                    numOfItems = jsonArray.length();
                }

                articlesDB.execSQL("DELETE FROM articles");

                for(int i = 0; i < numOfItems; i++){
                    String articleId = jsonArray.getString(i);
                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + articleId + ".json?print=pretty");

                    urlConnection = (HttpURLConnection) url.openConnection(); //Establishes connection and opens URL

                    inputStream = urlConnection.getInputStream();
                    inputStreamReader = new InputStreamReader(inputStream);

                    //Lets us parse through everything
                    data = inputStreamReader.read();

                    String articleInfo = "";

                    while(data != -1){
                        char current = (char) data;
                        articleInfo += current;
                        data = inputStreamReader.read();
                    }

                    //Log.i("Article Info", articleInfo);

                    JSONObject jsonObject = new JSONObject(articleInfo);

                    //First getting the API for article IDS, then gets info of each ID (title and URL), then get HTML for each element
                    //Takes a while to load because it has to make all the calls
                    if(!jsonObject.isNull("title") && !jsonObject.isNull("url")){
                        String articleTitle = jsonObject.getString("title");
                        String articleUrl = jsonObject.getString("url");


                        url = new URL(articleUrl);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        inputStream = urlConnection.getInputStream();
                        inputStreamReader = new InputStreamReader(inputStream);
                        data = inputStreamReader.read();

                        String articleContent = "";

                        while(data != -1){
                            char current = (char) data;
                            articleContent += current;
                            data = inputStreamReader.read();
                        }

                        Log.i("HTML", articleContent);
                        //Log.i("Title/URL", articleTitle + articleUrl);

                        //Inserts content into the SQLDB, uses a string and then will add values later
                        String sql = "INSERT INTO articles(articleId, title, content) VALUES (?,?,?)";
                        SQLiteStatement statement = articlesDB.compileStatement(sql);
                        //The statement protects the database from accidental deletions and bad code

                        statement.bindString(1, articleId);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleContent);

                        statement.execute();

                    }
                }

                Log.i("urlContent", result);

                return result;

            } catch (Exception e){
                e.printStackTrace();

            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            updateListView();
        }
    }
}
