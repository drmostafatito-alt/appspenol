package com.habla.espanol;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int INK=0xff263238, MUTED=0xff687a76, GREEN=0xff218746, DARK_GREEN=0xff176b39, BLUE=0xff386a80, SKY=0xffe8f3f4, PALE=0xffe8f8eb, LINE=0xffd9e6db, RED=0xffaa3644, RED_BG=0xffffefef, BG=0xfff7f9f5, GOLD=0xffffc857, CORAL=0xffff9558, LAVENDER=0xfff3f4e8;
    private static final int MIC_PERMISSION=17, NOTIFY_PERMISSION=18, PER_LESSON=5, EXPORT_REQUEST=101, IMPORT_REQUEST=102;
    private static final int INTRO=LearningLogic.INTRO, MEANING=LearningLogic.MEANING, LISTEN=LearningLogic.LISTEN, ORDER=LearningLogic.ORDER, TYPE=LearningLogic.TYPE, SPEAK=LearningLogic.SPEAK, REVERSE=LearningLogic.REVERSE, MISSING=LearningLogic.MISSING, MATCH=LearningLogic.MATCH, CONVERSE=LearningLogic.CONVERSE, TRANSFORM=LearningLogic.TRANSFORM;
    private final ArrayList<Track> tracks=new ArrayList<>();
    private final ArrayList<Exercise> queue=new ArrayList<>();
    private final Set<String> mistakes=new HashSet<>(), skippedItems=new HashSet<>();
    private final ArrayList<String> selectedWords=new ArrayList<>();
    private final ArrayList<Button> selectedChips=new ArrayList<>();
    private final ArrayList<Item> sessionItems=new ArrayList<>();
    private SharedPreferences prefs;
    private Typeface cairoRegular, cairoBold, nunitoRegular, nunitoBold;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private        LinearLayout root, body, answers, footer;
    private WordFlow bank, selectedBank;
    private MatchCards matchCards;
    private ScrollView scroll;
    private TextView feedback;
    private EditText input;
    private Button action;
    private String selectedId=null, selectedWord=null, screen="home";
    private int trackIndex=0, lessonIndex=0, cursor=0, correct=0, attempted=0, firstCorrect=0, firstAttempted=0, speechSkipped=0, listeningSkipped=0, matchMoves=0;
    private boolean dialogueMode=false;
    private int dialogueTurn=0;
    private Item dialogueItem;
    private boolean ttsReady=false, answered=false, pendingSpeech=false, reviewMode=false;
    private long lessonStarted;
    private String pendingExport=null;
    private static final int MAX_BACKUP_BYTES=4*1024*1024;
    private final Locale spanish=new Locale("es","ES");

    private static class Item {
        String id, es, ar;
        Item(String id,String es,String ar){this.id=id;this.es=es;this.ar=ar;}
    }
    private static class Lesson {
        String title; ArrayList<Item> items=new ArrayList<>();
        Lesson(String title){this.title=title;}
    }
    private static class Track {
        String title; ArrayList<Lesson> lessons=new ArrayList<>(); ArrayList<Item> items=new ArrayList<>();
        Track(String title){this.title=title;}
    }
    private static class Exercise {
        Item item; int type; boolean retry; String prompt="";
        Exercise(Item item,int type,boolean retry){this.item=item;this.type=type;this.retry=retry;}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::navigateBack);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs=getSharedPreferences("habla_progress",MODE_PRIVATE);
        cairoRegular=Typeface.createFromAsset(getAssets(),"fonts/cairo-regular.ttf");
        cairoBold=Typeface.createFromAsset(getAssets(),"fonts/cairo-bold.ttf");
        nunitoRegular=Typeface.createFromAsset(getAssets(),"fonts/nunito-regular.ttf");
        nunitoBold=Typeface.createFromAsset(getAssets(),"fonts/nunito-bold.ttf");
        trackIndex=prefs.getInt("last_track",0);
        try{loadContent();}catch(Exception e){throw new IllegalStateException("تعذر تحميل الدروس",e);}
        if(trackIndex<0||trackIndex>=tracks.size())trackIndex=0;
        if(prefs.getBoolean("reminders_enabled",true))ReminderReceiver.scheduleNext(this);
        tts=new TextToSpeech(this,this); if(!prefs.getBoolean("onboarding_done",false)&&prefs.getInt("complete_0",0)==0&&prefs.getInt("complete_1",0)==0&&prefs.getInt("xp",0)==0)showWelcome();else home();
    }
    private void loadContent() throws Exception {
        InputStream in=getAssets().open("content.json"); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n;
        while((n=in.read(buf))!=-1)bytes.write(buf,0,n); in.close();
        JSONArray ts=new JSONObject(bytes.toString("UTF-8")).getJSONArray("tracks");
        for(int t=0;t<ts.length();t++){
            JSONObject tr=ts.getJSONObject(t); Track track=new Track(tr.getString("title")); JSONArray units=tr.getJSONArray("units");
            for(int u=0;u<units.length();u++){
                JSONObject unit=units.getJSONObject(u); JSONArray list=unit.getJSONArray("items"); Lesson lesson=null;
                for(int i=0;i<list.length();i++){
                    if(i%PER_LESSON==0){lesson=new Lesson(unit.getString("title")+" • "+(i/PER_LESSON+1));track.lessons.add(lesson);}
                    JSONObject obj=list.getJSONObject(i); Item item=new Item(t+"_"+u+"_"+i,obj.getString("es"),obj.getString("ar"));
                    lesson.items.add(item); track.items.add(item);
                }
            } tracks.add(track);
        }
    }
    @Override public void onInit(int status){
        if(status==TextToSpeech.SUCCESS && tts.isLanguageAvailable(spanish)>=TextToSpeech.LANG_AVAILABLE && tts.setLanguage(spanish)>=TextToSpeech.LANG_AVAILABLE){tts.setSpeechRate(prefs.getFloat("tts_rate",.9f));ttsReady=true;}
        if(tts!=null){tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){@Override public void onStart(String id){} @Override public void onDone(String id){runOnUiThread(()->{if(tts!=null)tts.setSpeechRate(prefs.getFloat("tts_rate",.9f));});} @Override public void onError(String id){runOnUiThread(()->{if(tts!=null)tts.setSpeechRate(prefs.getFloat("tts_rate",.9f));});}});}
    }
    private void speak(String value,boolean slow){
        if(!ttsReady){toast("الصوت الإسباني غير متاح. نزّل الصوت الإسباني من إعدادات تحويل النص إلى كلام.");return;}
        String phrase=value.replaceAll("\\([^)]*\\)","").replaceAll("/as\\b","").replaceAll("/a\\b","").replace("...","").replace("…","").trim();
        float savedRate=prefs.getFloat("tts_rate",.9f);tts.setPitch(1.04f);tts.setSpeechRate(slow?Math.max(.5f,savedRate*.72f):savedRate);
        if(tts.speak(phrase,TextToSpeech.QUEUE_FLUSH,null,"lesson")!=TextToSpeech.SUCCESS)toast("تعذر تشغيل الصوت الإسباني.");
        tts.setSpeechRate(.9f);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private boolean dark(){return prefs!=null&&prefs.getBoolean("dark_mode",false);}
    private int surface(int color){if(!dark())return color;if(color==Color.WHITE)return 0xff25332d;if(color==BG)return 0xff121d18;if(color==PALE)return 0xff203e2e;if(color==SKY)return 0xff263750;if(color==LAVENDER)return 0xff333247;if(color==RED_BG)return 0xff482b30;if(color==LINE)return 0xff42554a;return color;}
    private int foreground(int color){if(!dark())return color;if(color==INK)return 0xffeff5ef;if(color==MUTED)return 0xffb5c6b9;if(color==DARK_GREEN||color==GREEN)return 0xff8ee3a1;if(color==BLUE)return 0xffa9c7ff;if(color==RED)return 0xffffa8ac;return color;}
    private GradientDrawable bg(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(surface(color));d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(2),surface(stroke));return d;}
    private Typeface fontFor(String value,boolean bold){boolean arabic=value==null||value.isEmpty()||value.matches("(?s).*[\\u0600-\\u06ff\\u0750-\\u077f\\u08a0-\\u08ff].*");return arabic?(bold?cairoBold:cairoRegular):(bold?nunitoBold:nunitoRegular);}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(foreground(color));v.setGravity(Gravity.CENTER_VERTICAL);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setTypeface(fontFor(value,bold));v.setLineSpacing(dp(3),1);return v;}
    private void space(LinearLayout box,int h){box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private Button button(String value,int color,int fg){Button b=new Button(this);b.setText(value);b.setTextColor(color==GREEN||color==BLUE||color==DARK_GREEN?fg:foreground(fg));b.setTextSize(16);b.setAllCaps(false);b.setTypeface(fontFor(value,true));b.setMinHeight(dp(54));b.setPadding(dp(14),dp(9),dp(14),dp(9));GradientDrawable shape=bg(color,color==Color.WHITE?LINE:0,18);b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(dark()?0x33ffffff:0x22000000),shape,null));return b;}
    private void full(LinearLayout box,View v){box.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(20),dp(20),dp(20),dp(20));c.setBackground(bg(Color.WHITE,LINE,24));return c;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private ProgressBar progress(int value,int max){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(Math.max(1,max));p.setProgress(value);p.setProgressTintList(ColorStateList.valueOf(dark()?0xff58cc72:GREEN));p.setProgressBackgroundTintList(ColorStateList.valueOf(surface(LINE)));p.setContentDescription("التقدم: "+value+" من "+max);return p;}
    private void setup(String title,boolean back,boolean fixedFooter){
        root=column();root.setBackgroundColor(surface(BG));getWindow().setStatusBarColor(surface(BG));getWindow().setNavigationBarColor(surface(BG));getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));root.setPadding(dp(20),dp(12),dp(20),dp(12));
        root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(dp(20),dp(12)+insets.getSystemWindowInsetTop(),dp(20),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        if(back){Button b=button("✕",Color.WHITE,MUTED);b.setTextSize(18);bar.addView(b,new LinearLayout.LayoutParams(dp(50),dp(48)));b.setOnClickListener(v->navigateBack());}
        TextView heading=text(title,18,INK,true);heading.setGravity(Gravity.CENTER);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));full(root,bar);space(root,12);
        scroll=new ScrollView(this);scroll.setFillViewport(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=column();scroll.addView(body);
        if(fixedFooter){footer=column();space(root,8);full(root,footer);}else footer=null;
        if(screen.equals("home")||screen.equals("path")||screen.equals("review")||screen.equals("dictionary")||screen.equals("profile")) addNavigation();
    }
    private void addNavigation(){
        LinearLayout nav=new LinearLayout(this);nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);nav.setPadding(0,dp(8),0,0);
        String[] labels={"الرئيسية","التعلم","المراجعة","القاموس","حسابي"};
        String[] screens={"home","path","review","dictionary","profile"};
        for(int i=0;i<labels.length;i++){
            final int tab=i;boolean active=screen.equals(screens[i]);
            String[] icons={"⌂","◈","↻","▤","◎"};TextView item=text(icons[i]+"\n"+labels[i],12,active?DARK_GREEN:MUTED,active);item.setGravity(Gravity.CENTER);item.setMinHeight(dp(58));item.setBackground(bg(active?PALE:BG,0,14));item.setContentDescription(labels[i]+(active?"، القسم الحالي":""));
            item.setOnClickListener(v->{if(tab==0)home();else if(tab==1)path();else if(tab==2)showReview();else if(tab==3)showDictionary();else showProfile();});
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        full(root,nav);
    }
    private void showWelcome(){
        screen="welcome";setup("أهلاً بك في Spanish 1000",false,false);
        mascot(body,"كل يوم خطوة تقربك من الإسبانية.");space(body,18);
        LinearLayout c=card();full(c,text("ابدأ رحلتك",24,INK,true));space(c,8);
        full(c,text("اتعلم الكلمات والعبارات، وراجعها في الوقت المناسب. تقدمك محفوظ على جهازك.",16,MUTED,false));space(c,16);
        full(c,text("اختار نقطة البداية",17,INK,true));space(c,4);full(c,text("المساران بيبدؤوا من أول درس؛ تقدر تغيّر المسار في أي وقت.",14,MUTED,false));space(c,6);Button trackChoice=button(tracks.get(trackIndex).title,Color.WHITE,BLUE);full(c,trackChoice);trackChoice.setOnClickListener(v->{trackIndex=(trackIndex+1)%tracks.size();prefs.edit().putInt("last_track",trackIndex).apply();trackChoice.setText(tracks.get(trackIndex).title);});space(c,12);
        full(c,text("هدفك اليومي",17,INK,true));space(c,8);
        Button goal=button(prefs.getInt("daily_goal",1)+" درس يومياً",Color.WHITE,BLUE);full(c,goal);goal.setOnClickListener(v->{int n=prefs.getInt("daily_goal",1)%3+1;prefs.edit().putInt("daily_goal",n).apply();goal.setText(n+" دروس يومياً");});space(c,12);
        Button start=button("ابدأ أول درس",GREEN,Color.WHITE);full(c,start);start.setOnClickListener(v->{prefs.edit().putBoolean("onboarding_done",true).apply();lessonIndex=completed(trackIndex);if(lessonIndex<tracks.get(trackIndex).lessons.size()){reviewMode=false;intro(tracks.get(trackIndex).lessons.get(lessonIndex).items);}else home();});full(body,c);
    }
    private void showReview(){
        screen="review";setup("المراجعة",false,false);
        LinearLayout c=card();full(c,text("راجع اللي اتعلمته",22,INK,true));space(c,8);
        full(c,text(dueCount()+" عنصر مستحق الآن · "+weakCount()+" يحتاج تدريبًا",16,MUTED,false));space(c,8);
        full(c,text(masteredCount()+" عنصر متقن حاليًا",15,DARK_GREEN,true));space(c,6);int total=0;for(Track track:tracks)total+=track.items.size();full(c,text("الإتقان من عناصر المسارين: "+percentage(masteredCount(),total),14,MUTED,false));full(c,progress(masteredCount(),total));space(c,16);
        Button review=button("ابدأ المراجعة",GREEN,Color.WHITE);full(c,review);review.setOnClickListener(v->startReview());full(body,c);space(body,12);
        Button flash=button("بطاقات المراجعة",Color.WHITE,BLUE);full(body,flash);flash.setOnClickListener(v->startFlashcards());space(body,8);
        Button weak=button("الكلمات التي تحتاج تدريبًا",Color.WHITE,BLUE);full(body,weak);weak.setOnClickListener(v->showWeakWords());
    }
    private void startFlashcards(){
        ArrayList<Item> due=new ArrayList<>();long now=System.currentTimeMillis();for(Track track:tracks)for(Item item:track.items){long when=prefs.getLong("due_"+item.id,0);if((when>0&&when<=now)||prefs.getInt("misses_"+item.id,0)>0)due.add(item);}
        if(due.isEmpty()){toast("مفيش بطاقات مستحقة للمراجعة دلوقتي.");return;}
        Collections.sort(due,(a,b)->Long.compare(prefs.getLong("due_"+a.id,0),prefs.getLong("due_"+b.id,0)));if(due.size()>PER_LESSON)due=new ArrayList<>(due.subList(0,PER_LESSON));showFlashcard(due,0,false);
    }
    private void showFlashcard(ArrayList<Item> cards,int index,boolean revealed){
        if(index>=cards.size()){showReview();toast("انتهت مراجعة البطاقات.");return;}
        screen="flashcard";Item item=cards.get(index);setup("بطاقة "+(index+1)+" / "+cards.size(),true,false);full(body,progress(index,cards.size()));space(body,18);
        LinearLayout c=card();TextView word=text(item.es,26,INK,true);word.setTextDirection(View.TEXT_DIRECTION_LTR);full(c,word);space(c,12);audioControls(c,item.es);space(c,12);
        if(revealed){full(c,text(item.ar,20,INK,true));space(c,12);full(c,text("افتكرتها إزاي؟",16,MUTED,false));String[] labels={"لم أتذكر","صعب","متوسط","سهل"};for(int q=0;q<labels.length;q++){final int quality=q;Button rate=button(labels[q],q==3?GREEN:Color.WHITE,q==3?Color.WHITE:BLUE);full(c,rate);space(c,6);rate.setOnClickListener(v->{long old=prefs.getLong("interval_"+item.id,0);float ease=prefs.getFloat("ease_"+item.id,2.5f);long interval=LearningLogic.flashcardInterval(old,ease,quality);prefs.edit().putLong("interval_"+item.id,interval).putLong("due_"+item.id,System.currentTimeMillis()+(quality==0?3600000L:interval)).putFloat("ease_"+item.id,Math.max(1.3f,Math.min(3f,ease+(quality==0?-.2f:quality==3?.1f:0)))).putInt("misses_"+item.id,quality==0?prefs.getInt("misses_"+item.id,0)+1:Math.max(0,prefs.getInt("misses_"+item.id,0)-1)).apply();showFlashcard(cards,index+1,false);});}}
        else{Button reveal=button("اظهر المعنى",GREEN,Color.WHITE);full(c,reveal);reveal.setOnClickListener(v->showFlashcard(cards,index,true));}full(body,c);
    }
    private void showProfile(){
        screen="profile";setup("حسابي",false,false);
        LinearLayout c=card();c.setBackground(bg(PALE,0,24));LinearLayout profileHead=new LinearLayout(this);profileHead.setGravity(Gravity.CENTER_VERTICAL);StudyBuddy profileBuddy=new StudyBuddy(this);profileHead.addView(profileBuddy,new LinearLayout.LayoutParams(dp(68),dp(68)));LinearLayout profileCopy=column();full(profileCopy,text("رحلتك في الإسباني",22,INK,true));full(profileCopy,text("تقدمك محفوظ على جهازك",14,MUTED,false));LinearLayout.LayoutParams profileParams=new LinearLayout.LayoutParams(0,-2,1);profileParams.setMargins(dp(12),0,0,0);profileHead.addView(profileCopy,profileParams);full(c,profileHead);space(c,8);
        full(c,text(prefs.getInt("xp",0)+" نقطة · "+prefs.getInt("streak",0)+" يوم متتالي",16,DARK_GREEN,true));space(c,6);full(c,text((completed(0)+completed(1))+" درس مكتمل · "+favorites().size()+" عنصر في المفضلة",15,MUTED,false));full(body,c);space(body,12);
        Button stats=button("إحصاءات التقدم",Color.WHITE,BLUE);full(body,stats);stats.setOnClickListener(v->showStats());space(body,8);
        Button settings=button("الإعدادات والنسخ الاحتياطي",Color.WHITE,BLUE);full(body,settings);settings.setOnClickListener(v->showLearningSettings());space(body,12);stickerShelf(body);
    }
    private int completed(int t){return Math.max(0,Math.min(prefs.getInt("complete_"+t,0),tracks.get(t).lessons.size()));}
    private int completedItems(int t){ArrayList<Integer> sizes=new ArrayList<>();for(Lesson l:tracks.get(t).lessons)sizes.add(l.items.size());return LearningLogic.completedItems(sizes,completed(t));}
    private String percentage(int done,int total){return (total==0?0:done*100/total)+"٪";}
    private void mascot(LinearLayout box,String caption){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);CharacterArt.CharacterView buddy=new CharacterArt.CharacterView(this,CharacterArt.Cast.SOL,CharacterArt.Mood.WELCOME,dark(),false);row.addView(buddy,new LinearLayout.LayoutParams(dp(76),dp(76)));TextView bubble=text(caption,15,INK,true);bubble.setPadding(dp(14),dp(10),dp(14),dp(10));bubble.setBackground(bg(Color.WHITE,LINE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(8),0,0,0);row.addView(bubble,p);full(box,row);}
    private void home(){
        screen="home";setup("Spanish 1000",false,false);
        Track current=tracks.get(trackIndex);int next=completed(trackIndex);
        LinearLayout welcome=new LinearLayout(this);welcome.setGravity(Gravity.CENTER_VERTICAL);LinearLayout greeting=column();full(greeting,text("¡Hola! أهلًا بيك",25,INK,true));space(greeting,4);full(greeting,text("كل يوم أقرب للإسباني",15,MUTED,false));welcome.addView(greeting,new LinearLayout.LayoutParams(0,-2,1));CharacterArt.CharacterView buddy=new CharacterArt.CharacterView(this,CharacterArt.Cast.SOL,CharacterArt.Mood.HAPPY,dark(),false);welcome.addView(buddy,new LinearLayout.LayoutParams(dp(72),dp(72)));full(body,welcome);space(body,16);
        LinearLayout indicators=new LinearLayout(this);indicators.setGravity(Gravity.CENTER_VERTICAL);TextView streak=text("سلسلتك  "+prefs.getInt("streak",0)+" يوم",14,DARK_GREEN,true),xp=text("نقاطك  "+prefs.getInt("xp",0)+" XP",14,DARK_GREEN,true);streak.setGravity(Gravity.CENTER);xp.setGravity(Gravity.CENTER);streak.setPadding(dp(8),dp(12),dp(8),dp(12));xp.setPadding(dp(8),dp(12),dp(8),dp(12));streak.setBackground(bg(PALE,0,16));xp.setBackground(bg(LAVENDER,0,16));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.setMargins(0,0,dp(8),0);indicators.addView(streak,ip);indicators.addView(xp,new LinearLayout.LayoutParams(0,-2,1));full(body,indicators);space(body,20);
        TextView section=text("رحلتك النهارده",19,INK,true);full(body,section);space(body,8);
        LinearLayout continuation=card();continuation.setBackground(bg(PALE,0,24));full(continuation,text(current.title,14,DARK_GREEN,true));space(continuation,6);full(continuation,text(next<current.lessons.size()?current.lessons.get(next).title:"أكملت المسار — راجع دروسك",21,INK,true));space(continuation,8);full(continuation,text(completedItems(trackIndex)+" من "+current.items.size()+" عنصر في المسار",14,MUTED,false));space(continuation,8);full(continuation,progress(completedItems(trackIndex),current.items.size()));space(continuation,16);
        Button resume=button(next==0?"ابدأ التعلم":"كمّل التعلم",GREEN,Color.WHITE);full(continuation,resume);resume.setOnClickListener(v->{if(next<current.lessons.size()){lessonIndex=next;reviewMode=false;intro(current.lessons.get(next).items);}else path();});full(body,continuation);space(body,16);
        dailyGoalCard(body);space(body,12);
        LinearLayout reviewPanel=card();full(reviewPanel,text("تدريب مناسب ليك",18,INK,true));space(reviewPanel,5);full(reviewPanel,text(dueCount()>0?dueCount()+" عنصر موعد مراجعته وصل":"ابدأ درسًا جديدًا أو راجع الكلمات اللي محتاجة تدريب",14,MUTED,false));space(reviewPanel,10);Button todayReview=button(dueCount()>0?"ابدأ المراجعة":"افتح المراجعة",Color.WHITE,BLUE);full(reviewPanel,todayReview);todayReview.setOnClickListener(v->showReview());full(body,reviewPanel);space(body,20);
        full(body,text("اختار مسارك",21,INK,true));space(body,10);
        for(int t=0;t<tracks.size();t++){
            final int index=t;Track tr=tracks.get(t);int done=completed(t),items=completedItems(t),remaining=tr.items.size()-items;LinearLayout c=card();
            full(c,text((t==0?"📘  ":"💬  ")+tr.title,20,INK,true));space(c,8);
            full(c,text(percentage(items,tr.items.size())+" مكتمل  •  "+items+" من "+tr.items.size()+" عنصر",15,INK,true));space(c,8);full(c,progress(items,tr.items.size()));space(c,8);
            full(c,text("خلصت "+done+" درس  •  باقي "+(tr.lessons.size()-done)+" درس و"+remaining+" عنصر",14,MUTED,false));space(c,14);
            Button go=button(done==0?"ابدأ المسار":"افتح مسارك",t==0?GREEN:BLUE,Color.WHITE);full(c,go);go.setOnClickListener(v->{trackIndex=index;prefs.edit().putInt("last_track",index).apply();path();});full(body,c);space(body,12);
        }
        Button review=button("🔄  مراجعة العناصر المستحقة  •  "+dueCount(),Color.WHITE,BLUE);full(body,review);review.setOnClickListener(v->startReview());space(body,8);
        Button weak=button("🧠  الكلمات التي تحتاج تدريبًا  •  "+weakCount(),Color.WHITE,BLUE);full(body,weak);weak.setOnClickListener(v->showWeakWords());space(body,8);
        Button dialogue=button("🎭  محادثات الحياة اليومية — Real Life Spanish",Color.WHITE,GREEN);full(body,dialogue);dialogue.setOnClickListener(v->showScenarios());space(body,8);
        Button stats=button("📊  إحصاءاتك وسلسلة أيامك",Color.WHITE,BLUE);full(body,stats);stats.setOnClickListener(v->showStats());space(body,8);
        Button dictionary=button("📖  القاموس — ابحث وسمّع وافضّل",Color.WHITE,BLUE);full(body,dictionary);dictionary.setOnClickListener(v->showDictionary());space(body,8);
        Button grammar=button("📚  قواعد إسبانية سريعة",Color.WHITE,BLUE);full(body,grammar);grammar.setOnClickListener(v->showGrammar());space(body,8);
        Button settings=button("⚙  التقدم الاحتياطي والسرعة والهدف",Color.WHITE,MUTED);full(body,settings);settings.setOnClickListener(v->showLearningSettings());space(body,12);
        stickerShelf(body);
        full(body,text("الاستماع والنطق يعتمدان على خدمات هاتفك. التحدث قد يحتاج إنترنت.",13,MUTED,false));
    }
    private void stickerShelf(LinearLayout box){
        int total=completed(0)+completed(1);LinearLayout c=card();full(c,text("ملصقات رحلتك 🏅",18,INK,true));space(c,8);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels={"أول درس","خمس دروس","صاحب سلسلة"};String[] icons={"🌱","🗺️","🔥"};int[] thresholds={1,5,3};
        for(int i=0;i<labels.length;i++){boolean unlocked=i==2?prefs.getInt("streak",0)>=thresholds[i]:total>=thresholds[i];LinearLayout badge=column();badge.setGravity(Gravity.CENTER);TextView icon=text(unlocked?icons[i]:"🔒",25,unlocked?GOLD:MUTED,true);icon.setGravity(Gravity.CENTER);badge.addView(icon,new LinearLayout.LayoutParams(-1,dp(42)));TextView caption=text(labels[i],11,unlocked?INK:MUTED,unlocked);caption.setGravity(Gravity.CENTER);badge.addView(caption);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);row.addView(badge,p);}
        full(c,row);full(box,c);space(box,12);
    }
    private void path(){
        screen="path";Track track=tracks.get(trackIndex);setup(track.title,true,false);int done=completed(trackIndex),items=completedItems(trackIndex);
        LinearLayout banner=card();int pathColor=trackIndex==0?DARK_GREEN:BLUE;banner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{pathColor,trackIndex==0?GREEN:Color.rgb(93,142,226)}));((GradientDrawable)banner.getBackground()).setCornerRadius(dp(22));
        full(banner,text("رحلتك في الإسباني ✨",23,Color.WHITE,true));space(banner,7);
        full(banner,text("خلصت "+items+" من "+track.items.size()+" عنصر • باقي "+(track.items.size()-items),15,Color.WHITE,true));space(banner,9);
        ProgressBar bp=progress(items,track.items.size());bp.setProgressTintList(ColorStateList.valueOf(Color.WHITE));full(banner,bp);space(banner,7);
        full(banner,text(done+" من "+track.lessons.size()+" درس • "+percentage(items,track.items.size()),14,Color.WHITE,false));full(body,banner);space(body,16);
        if(done<track.lessons.size()){
            final int next=done;Button start=button("▶  ابدأ الدرس التالي",GREEN,Color.WHITE);full(body,start);start.setOnClickListener(v->{lessonIndex=next;reviewMode=false;intro(track.lessons.get(next).items);});space(body,8);
        }else{full(body,text("أكملت المسار! راجع أي درس وقت ما تحب. 🎉",16,GREEN,true));space(body,8);}
        if(done>0){Button quiz=button("اختبر نفسك — عناصر عشوائية من دروسك المكتملة",Color.WHITE,BLUE);full(body,quiz);quiz.setOnClickListener(v->startQuiz());space(body,16);}else space(body,16);
        full(body,text("الوحدات والدروس",21,INK,true));space(body,6);
        String previousUnit="";
        for(int i=0;i<track.lessons.size();i++){
            final int index=i;Lesson l=track.lessons.get(i);boolean locked=i>done,finished=i<done,current=i==done;
            String unit=l.title.split(" • ")[0];
            if(!unit.equals(previousUnit)){previousUnit=unit;int unitTotal=0,unitCompleted=0;for(int j=i;j<track.lessons.size()&&track.lessons.get(j).title.split(" • ")[0].equals(unit);j++){unitTotal++;if(j<done)unitCompleted++;}space(body,12);LinearLayout unitHeader=card();unitHeader.setBackground(bg(PALE,0,18));full(unitHeader,text(unit,18,INK,true));space(unitHeader,4);full(unitHeader,text(unitCompleted+" من "+unitTotal+" درس مكتمل",14,MUTED,false));space(unitHeader,6);full(unitHeader,progress(unitCompleted,unitTotal));full(body,unitHeader);space(body,8);}
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setGravity(i%3==0?Gravity.START:i%3==1?Gravity.CENTER_HORIZONTAL:Gravity.END);row.setPadding(dp(38),dp(3),dp(38),dp(3));
            Button node=button(finished?"✓":current?"▶":"·",finished?DARK_GREEN:current?GREEN:LINE,finished||current?Color.WHITE:INK);
            node.setTextSize(23);node.setBackground(bg(finished?DARK_GREEN:current?GREEN:LINE,0,40));
            row.addView(node,new LinearLayout.LayoutParams(dp(68),dp(68)));node.setContentDescription(l.title+(finished?" مكتمل":current?" الدرس التالي":" مقفل"));
            if(!locked)node.setOnClickListener(v->{lessonIndex=index;reviewMode=false;intro(track.lessons.get(index).items);});else node.setOnClickListener(v->toast("أكمل الدروس السابقة لفتح هذا الدرس."));
            TextView label=text(current?"الدرس الحالي":finished?"مكتمل":"مقفل",13,current?DARK_GREEN:MUTED,true);label.setGravity(Gravity.CENTER);LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(dp(100),-2);labelParams.topMargin=dp(4);row.addView(label,labelParams);full(body,row);if(i+1<track.lessons.size()&&track.lessons.get(i+1).title.split(" • ")[0].equals(unit)){TextView connector=text("·  ·  ·",16,finished?GREEN:MUTED,true);connector.setGravity(Gravity.CENTER);full(body,connector);}
        }
    }
    private void intro(ArrayList<Item> items){begin(items);}
    private void startReview(){
        ArrayList<Item> due=new ArrayList<>();long now=System.currentTimeMillis();for(Track t:tracks)for(Item item:t.items){long when=prefs.getLong("due_"+item.id,0);if((when>0&&when<=now)||prefs.getInt("misses_"+item.id,0)>0)due.add(item);}
        if(due.isEmpty()){toast("مفيش مراجعة مستحقة دلوقتي. أكمل درسًا وارجع لاحقًا.");return;}
        Collections.sort(due,(a,b)->Long.compare(prefs.getLong("due_"+a.id,0),prefs.getLong("due_"+b.id,0)));
        ArrayList<Item> prioritized=new ArrayList<>();for(Item item:due)if(prefs.getLong("interval_"+item.id,0)==0)prioritized.add(item);for(Item item:due)if(!prioritized.contains(item))prioritized.add(item);
        if(prioritized.size()>PER_LESSON)prioritized=new ArrayList<>(prioritized.subList(0,PER_LESSON));reviewMode=true;intro(prioritized);
    }
    private int dueCount(){long now=System.currentTimeMillis();int count=0;for(Track t:tracks)for(Item item:t.items){long due=prefs.getLong("due_"+item.id,0);if(due>0&&due<=now)count++;}return count;}
    private void dailyGoalCard(LinearLayout box){
        int goal=prefs.getInt("daily_goal",1);String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());int done=prefs.getString("daily_date","").equals(today)?prefs.getInt("daily_lessons",0):0;
        LinearLayout daily=card();daily.setBackground(bg(LAVENDER,0,20));full(daily,text("🎯  هدفك اليومي",17,INK,true));space(daily,5);full(daily,goalRow(done,goal));full(box,daily);
    }
    private LinearLayout goalRow(int done,int goal){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        int pct=goal<=0?0:Math.min(100,done*100/goal);
        DesignSystem.RingProgressView ring=new DesignSystem.RingProgressView(this,pct,done+"/"+goal,"درس اليوم",foreground(GREEN),surface(LINE),foreground(INK),foreground(MUTED),cairoBold,nunitoRegular);
        row.addView(ring,new LinearLayout.LayoutParams(dp(92),dp(92)));
        LinearLayout copy=column();
        full(copy,text(done>=goal?"وصلت لهدفك اليوم 🎉":"باقي "+(goal-done)+" درس لهدف اليوم",16,INK,true));space(copy,4);
        full(copy,text("الهدف: "+goal+" درس يوميًا · السلسلة: "+prefs.getInt("streak",0)+" يوم",13,MUTED,false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(12),0,0,0);row.addView(copy,p);
        return row;
    }
    private void showLearningSettings(){
        screen="settings";setup("إعدادات التعلم",true,false);LinearLayout c=card();full(c,text("🎯 هدف الدروس اليومي",19,INK,true));space(c,8);int goal=prefs.getInt("daily_goal",1);TextView value=text(goal+" درس يوميًا",17,GREEN,true);value.setGravity(Gravity.CENTER);full(c,value);space(c,8);LinearLayout row=new LinearLayout(this);Button less=button("−",Color.WHITE,BLUE),more=button("+",Color.WHITE,BLUE);row.addView(less,new LinearLayout.LayoutParams(0,dp(54),1));row.addView(more,new LinearLayout.LayoutParams(0,dp(54),1));full(c,row);less.setOnClickListener(v->{int n=Math.max(1,prefs.getInt("daily_goal",1)-1);prefs.edit().putInt("daily_goal",n).apply();value.setText(n+" درس يوميًا");});more.setOnClickListener(v->{int n=Math.min(10,prefs.getInt("daily_goal",1)+1);prefs.edit().putInt("daily_goal",n).apply();value.setText(n+" درس يوميًا");});space(c,20);full(c,text("🔊 سرعة النطق الإسباني",19,INK,true));space(c,5);float rate=prefs.getFloat("tts_rate",.9f);TextView speed=text(String.format(Locale.US,"%.1f×",rate),17,GREEN,true);speed.setGravity(Gravity.CENTER);full(c,speed);android.widget.SeekBar seek=new android.widget.SeekBar(this);seek.setMax(15);seek.setProgress(Math.round((rate-.5f)*10));full(c,seek);seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(android.widget.SeekBar s,int p,boolean user){float r=.5f+p*.1f;speed.setText(String.format(Locale.US,"%.1f×",r));if(user){prefs.edit().putFloat("tts_rate",r).apply();if(ttsReady)tts.setSpeechRate(r);}}public void onStartTrackingTouch(android.widget.SeekBar s){}public void onStopTrackingTouch(android.widget.SeekBar s){}});space(c,16);
        full(c,text("المظهر",19,INK,true));space(c,8);Button theme=button(dark()?"الوضع الداكن: مفعّل":"الوضع الداكن: غير مفعّل",Color.WHITE,BLUE);full(c,theme);theme.setOnClickListener(v->{prefs.edit().putBoolean("dark_mode",!dark()).apply();showLearningSettings();});space(c,16);
        boolean remindersOn=prefs.getBoolean("reminders_enabled",true);
        full(c,text("🔔 التذكير اليومي",19,INK,true));space(c,5);
        Button toggle=button(remindersOn?"المذكّرات مفعّلة — اضغط للإيقاف":"المذكّرات موقوفة — اضغط لتفعيلها",remindersOn?PALE:Color.WHITE,remindersOn?DARK_GREEN:BLUE);full(c,toggle);space(c,5);
        full(c,text("نذكّرك يوميًا بعد الثامنة مساءً فقط إذا لم تُكمل هدف اليوم.",14,MUTED,false));space(c,8);
        full(c,text("💾 نسخ التقدم الاحتياطي",19,INK,true));space(c,5);
        full(c,text("صدّر تقدمك إلى ملف لتستعيده بعد حذف التطبيق أو عند الانتقال إلى جهاز آخر.",14,MUTED,false));space(c,8);
        LinearLayout backupRow=new LinearLayout(this);Button expB=button("📤 تصدير",GREEN,Color.WHITE),impB=button("📥 استيراد",Color.WHITE,BLUE);backupRow.addView(expB,new LinearLayout.LayoutParams(0,dp(54),1));backupRow.addView(impB,new LinearLayout.LayoutParams(0,dp(54),1));full(c,backupRow);
        full(body,c);space(body,10);full(body,text("الإعدادات تُحفظ على هذا الجهاز. توفر الصوت يعتمد على محرك TTS المثبت.",14,MUTED,false));
        expB.setOnClickListener(v->exportProgress());impB.setOnClickListener(v->importProgressPicker());
        toggle.setOnClickListener(v->{
            if(remindersOn){prefs.edit().putBoolean("reminders_enabled",false).apply();ReminderReceiver.cancel(this);toast("أوقفنا التذكير اليومي.");showLearningSettings();return;}
            prefs.edit().putBoolean("reminders_enabled",true).apply();
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY_PERMISSION);return;}
            ReminderReceiver.scheduleNext(this);toast("سنذكرك يوميًا إذا لم تُكمل هدفك.");showLearningSettings();
        });}
    private EditText dicSearch;private TextView dicCount;private LinearLayout dicList,dicChips;private int dicMode=-2,dicLimit=80;
    private final ArrayList<Button> dicChipButtons=new ArrayList<>();private final ArrayList<Integer> dicChipModes=new ArrayList<>();
    private Set<String> favorites(){Object value=prefs.getAll().get("dictionary_favorites");if(value instanceof Set)return new HashSet<>((Set<String>)value);if(value instanceof String){try{String raw=(String)value;if(raw.startsWith("s:"))raw=raw.substring(2);Set<String> recovered=LearningLogic.legacyFavorites(raw);saveFavorites(recovered);return recovered;}catch(IllegalArgumentException e){return new HashSet<>();}}return new HashSet<>();}
    private void saveFavorites(Set<String> set){prefs.edit().putStringSet("dictionary_favorites",new HashSet<>(set)).apply();}
    private void showDictionary(){
        screen="dictionary";dicMode=-2;dicLimit=80;setup("القاموس 📖",true,false);
        LinearLayout head=card();head.setBackground(bg(PALE,0,24));full(head,text("كلماتك في مكان واحد",22,INK,true));space(head,4);full(head,text("ابحث بالإسبانية أو بالعربي، واسمع النطق واحفظ المفضلة.",14,MUTED,false));space(head,8);
        dicSearch=new EditText(this);dicSearch.setTypeface(cairoRegular);dicSearch.setSingleLine(true);dicSearch.setTextSize(17);dicSearch.setTextColor(foreground(INK));dicSearch.setHintTextColor(foreground(MUTED));dicSearch.setHint("اكتب مثل: agua أو ماء");dicSearch.setPadding(dp(15),dp(12),dp(15),dp(12));dicSearch.setBackground(bg(Color.WHITE,LINE,16));
        dicSearch.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable s){dicLimit=80;renderDictionary();}});
        full(head,dicSearch);space(head,10);
        dicChips=new LinearLayout(this);dicChipButtons.clear();dicChipModes.clear();
        addDicChip("الكل",-2);addDicChip("⭐ المفضلة",-1);for(int t=0;t<tracks.size();t++)addDicChip((t==0?"📘 ":"💬 ")+tracks.get(t).title,t);
        HorizontalScrollView chipScroll=new HorizontalScrollView(this);chipScroll.setHorizontalScrollBarEnabled(false);chipScroll.addView(dicChips);full(head,chipScroll);
        full(body,head);space(body,10);
        dicCount=text("",14,MUTED,true);full(body,dicCount);space(body,8);
        dicList=column();full(body,dicList);
        renderDictionary();
    }
    private void addDicChip(String label,int mode){
        Button chip=button(label,Color.WHITE,INK);chip.setTextSize(13);chip.setMinHeight(dp(38));chip.setPadding(dp(12),dp(4),dp(12),dp(4));
        chip.setBackground(bg(mode==dicMode?SKY:Color.WHITE,mode==dicMode?BLUE:LINE,14));
        chip.setOnClickListener(v->{dicMode=mode;dicLimit=80;refreshDicChips();renderDictionary();});
        dicChipButtons.add(chip);dicChipModes.add(mode);
        dicChips.addView(chip,new LinearLayout.LayoutParams(-2,-2));View gap=new View(this);gap.setMinimumWidth(dp(6));dicChips.addView(gap,new LinearLayout.LayoutParams(dp(6),1));
    }
    private void refreshDicChips(){for(int i=0;i<dicChipButtons.size();i++){int mode=dicChipModes.get(i);dicChipButtons.get(i).setBackground(bg(mode==dicMode?SKY:Color.WHITE,mode==dicMode?BLUE:LINE,14));}}
    private void renderDictionary(){
        if(dicList==null||dicCount==null)return;
        Set<String> fav=favorites();String query=dicSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<Item> results=new ArrayList<>();
        for(int t=0;t<tracks.size();t++){Track track=tracks.get(t);
            for(Item item:track.items){
                if(dicMode==-1&&!fav.contains(item.id))continue;
                if(dicMode>=0&&dicMode!=t)continue;
                if(!query.isEmpty()&&!item.es.toLowerCase(Locale.ROOT).contains(query)&&!item.ar.contains(query))continue;
                results.add(item);
            }
        }
        String count=results.size()+" نتيجة";
        if(results.size()>dicLimit)count+=" — بتعرض أول "+dicLimit+" بس";
        dicCount.setText(count);dicList.removeAllViews();
        int shown=Math.min(results.size(),dicLimit);
        for(int r=0;r<shown;r++){
            Item item=results.get(r);boolean starred=fav.contains(item.id);
            int misses=prefs.getInt("misses_"+item.id,0);long interval=prefs.getLong("interval_"+item.id,0),due=prefs.getLong("due_"+item.id,0);
            String mastery=misses>0?"عندك أخطاء متكررة فيها":(due>System.currentTimeMillis()&&interval>=86400000L?"محفوظة كويس ✓":"");
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setBackground(bg(Color.WHITE,LINE,20));
            LinearLayout copy=column();
            TextView es=text(item.es,17,INK,true);es.setTextDirection(View.TEXT_DIRECTION_LTR);full(copy,es);
            if(!item.ar.isEmpty())full(copy,text(item.ar,13,MUTED,false));
            if(!mastery.isEmpty())full(copy,text(mastery,12,misses>0?RED:GREEN,true));
            row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));copy.setOnClickListener(v->showWordDetails(item));copy.setContentDescription("تفاصيل "+item.es);
            Button star=button(starred?"★":"☆",Color.WHITE,starred?GOLD:MUTED);star.setTextSize(20);star.setPadding(0,0,0,0);row.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));star.setContentDescription(starred?"إزالة من المفضلة":"أضف للمفضلة");
            Button say=button("🔊",SKY,BLUE);say.setTextSize(15);say.setPadding(0,0,0,0);row.addView(say,new LinearLayout.LayoutParams(dp(48),dp(48)));say.setContentDescription("تشغيل النطق");
            star.setOnClickListener(v->{Set<String> now=favorites();boolean keep;if(now.contains(item.id)){now.remove(item.id);keep=false;}else{now.add(item.id);keep=true;}saveFavorites(now);star.setText(keep?"★":"☆");star.setTextColor(keep?GOLD:foreground(MUTED));star.setContentDescription(keep?"إزالة من المفضلة":"أضف للمفضلة");if(dicMode==-1)renderDictionary();});
            say.setOnClickListener(v->speak(item.es,false));
            dicList.addView(row,new LinearLayout.LayoutParams(-1,-2));space(dicList,6);
        }
        if(shown<results.size()){Button more=button("عرض المزيد",Color.WHITE,BLUE);full(dicList,more);more.setOnClickListener(v->{dicLimit+=80;renderDictionary();});}
        if(results.isEmpty())full(dicList,text("لا توجد نتائج مطابقة.",15,MUTED,false));
        space(dicList,14);
    }
    private void showWordDetails(Item item){
        screen="word_detail";setup("تفاصيل العبارة",true,false);LinearLayout c=card();
        c.setBackground(bg(PALE,0,24));TextView word=text(item.es,28,INK,true);word.setTextDirection(View.TEXT_DIRECTION_LTR);full(c,word);space(c,8);full(c,text(item.ar,19,INK,true));space(c,12);audioControls(c,item.es);space(c,12);
        int misses=prefs.getInt("misses_"+item.id,0);long interval=prefs.getLong("interval_"+item.id,0);String status=misses>0?"تحتاج إلى مراجعة":interval>0?"قيد المراجعة المتباعدة":"لم تتم مراجعتها بعد";full(c,text(status,15,MUTED,false));long due=prefs.getLong("due_"+item.id,0);if(due>0)full(c,text("المراجعة التالية: "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(due)),14,MUTED,false));space(c,12);
        Button practice=button("تدرّب على العبارة",GREEN,Color.WHITE);full(c,practice);practice.setOnClickListener(v->{ArrayList<Item> one=new ArrayList<>();one.add(item);reviewMode=true;begin(one);});full(body,c);
    }
    private void showGrammar(){
        screen="grammar";setup("قواعد إسبانية سريعة 📚",true,false);
        full(body,text("بطاقات صغيرة بتشرح القاعدة اللي ورا الجمل اللي بتحفظها. اقراها بالراحة بعد درس، وهتثبت أسرع.",14,MUTED,false));space(body,10);
        for(String[] tip:GrammarTips.TIPS){LinearLayout c=card();full(c,text(tip[0],18,GREEN,true));space(c,6);full(c,text(tip[1],15,INK,false));full(body,c);space(body,10);}
    }
    private void startQuiz(){
        Track track=tracks.get(trackIndex);int done=Math.min(completed(trackIndex),track.lessons.size());
        ArrayList<Item> pool=new ArrayList<>();for(int i=0;i<done;i++)pool.addAll(track.lessons.get(i).items);
        if(pool.isEmpty()){toast("أكمل درسًا واحدًا الأول، ثم جرّب.");return;}
        Collections.shuffle(pool);while(pool.size()>PER_LESSON)pool.remove(pool.size()-1);
        reviewMode=true;begin(pool);
    }
    private int masteredCount(){long now=System.currentTimeMillis();int n=0;for(Track t:tracks)for(Item i:t.items)if(prefs.getLong("interval_"+i.id,0)>=2L*86400000L&&prefs.getLong("due_"+i.id,0)>now)n++;return n;}
    private void showStats(){
        screen="stats";setup("إحصاءاتك 📊",true,false);
        int streak=prefs.getInt("streak",0),xp=prefs.getInt("xp",0);int totalLessons=completed(0)+completed(1);
        int firstC=prefs.getInt("total_first_correct",0),firstA=prefs.getInt("total_first_attempted",0);
        Calendar week=Calendar.getInstance();SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.US);int weekC=0,weekA=0;for(int d=0;d<7;d++){weekC+=prefs.getInt("acc_"+fmt.format(week.getTime())+"_c",0);weekA+=prefs.getInt("acc_"+fmt.format(week.getTime())+"_a",0);week.add(Calendar.DAY_OF_YEAR,-1);}
        LinearLayout hero=card();hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{DARK_GREEN,GREEN}));((GradientDrawable)hero.getBackground()).setCornerRadius(dp(24));
        full(hero,text("🔥  "+streak+" يوم متتالي",23,Color.WHITE,true));space(hero,7);full(hero,text("✦  "+xp+" نقطة تراكمية  •  "+totalLessons+" درس مكتمل",15,Color.rgb(221,245,234),true));full(body,hero);space(body,14);
        LinearLayout acc=card();full(acc,text("دقة الإجابات",18,INK,true));space(acc,8);
        full(acc,text("تراكمية: "+(firstA==0?"—":percentage(firstC,firstA)),16,INK,true));space(acc,6);
        full(acc,text("هذا الأسبوع: "+(weekA==0?"—":percentage(weekC,weekA)),16,INK,true));space(acc,6);full(acc,progress(firstC,Math.max(1,firstA)));full(body,acc);space(body,12);
        LinearLayout vocab=card();full(vocab,text("حصيلتك في الإسباني",18,INK,true));space(vocab,8);
        full(vocab,text((completedItems(0)+completedItems(1))+" عنصر في الدروس المكتملة",16,INK,true));space(vocab,6);full(vocab,text(""+masteredCount()+" عبارة متقنة (2 يوم أو أكثر بين المراجعات)",15,INK,true));space(vocab,6);
        full(vocab,text(""+dueCount()+" عبارة مستحقة للمراجعة الآن",15,INK,true));space(vocab,6);
        full(vocab,text(""+weakCount()+" عبارة ما زالت تحتاج تدريبًا",15,INK,true));full(body,vocab);space(body,12);
        LinearLayout history=card();full(history,text("سجل الإجابات خلال آخر ٧ أيام",18,INK,true));space(history,6);Calendar day=Calendar.getInstance();for(int i=0;i<7;i++){String key=fmt.format(day.getTime());int a=prefs.getInt("acc_"+key+"_a",0),correctDay=prefs.getInt("acc_"+key+"_c",0);full(history,text(key+"   •   "+(a==0?"لا توجد إجابات مسجلة":correctDay+" من "+a+" صحيحة"),14,INK,false));if(a>0)full(history,progress(correctDay,a));space(history,6);day.add(Calendar.DAY_OF_YEAR,-1);}full(body,history);space(body,12);
        Button homeButton=button("العودة للرئيسية",GREEN,Color.WHITE);full(body,homeButton);homeButton.setOnClickListener(v->home());
    }
    private String encodePref(Object value){
        if(value instanceof Boolean)return "b:"+value;
        if(value instanceof Integer)return "i:"+value;
        if(value instanceof Long)return "l:"+value;
        if(value instanceof Float)return "f:"+value;
        if(value instanceof Set)return "set:"+new JSONArray((Set<?>)value).toString();
        return "s:"+value;
    }
    private String progressSnapshot() throws Exception {JSONObject data=new JSONObject();for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet())data.put(entry.getKey(),encodePref(entry.getValue()));return data.toString();}
    private void exportProgress(){
        try{pendingExport=progressSnapshot();
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"habla-backup.json");startActivityForResult(intent,EXPORT_REQUEST);
        }catch(Exception e){toast("تعذر تجهيز النسخة الاحتياطية.");}
    }
    private void importProgressPicker(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");startActivityForResult(intent,IMPORT_REQUEST);
    }
    @Override public void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==EXPORT_REQUEST&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(pendingExport==null)pendingExport=progressSnapshot();out.write(pendingExport.getBytes("UTF-8"));toast("تم تصدير تقدمك. احفظ الملف في مكان آمن.");}catch(Exception e){toast("تعذر كتابة الملف.");}
            pendingExport=null;
        }
        else if(requestCode==IMPORT_REQUEST&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(bytes.size()>MAX_BACKUP_BYTES-n)throw new IllegalArgumentException("Backup too large");bytes.write(buf,0,n);}
                JSONObject parsed=new JSONObject(bytes.toString("UTF-8"));java.util.Map<String,Object> decoded=new java.util.HashMap<>();Iterator<String> keys=parsed.keys();
                while(keys.hasNext()){
                    String key=keys.next();Object raw=parsed.get(key);if(!(raw instanceof String))throw new IllegalArgumentException("Invalid backup value");String encoded=(String)raw;Object value;
                    if(key.equals("dictionary_favorites")&&encoded.startsWith("s:"))value=LearningLogic.legacyFavorites(encoded.substring(2));
                    else if(encoded.startsWith("b:")){String v=encoded.substring(2);if(!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException("Invalid boolean");value=Boolean.valueOf(v);}
                    else if(encoded.startsWith("i:"))value=Integer.valueOf(encoded.substring(2));
                    else if(encoded.startsWith("l:"))value=Long.valueOf(encoded.substring(2));
                    else if(encoded.startsWith("f:")){float v=Float.parseFloat(encoded.substring(2));if(Float.isNaN(v)||Float.isInfinite(v))throw new IllegalArgumentException("Invalid float");value=v;}
                    else if(encoded.startsWith("set:")){JSONArray arr=new JSONArray(encoded.substring(4));Set<String> values=new HashSet<>();for(int i=0;i<arr.length();i++)values.add(arr.getString(i));value=values;}
                    else if(encoded.startsWith("s:"))value=encoded.substring(2);else throw new IllegalArgumentException("Invalid backup type");
                    if(!LearningLogic.validPreferenceType(key,value))throw new IllegalArgumentException("Invalid preference type");
                    Object existing=prefs.getAll().get(key);if(existing!=null&&!key.equals("dictionary_favorites")&&!existing.getClass().equals(value.getClass())&&!(existing instanceof Set&&value instanceof Set))throw new IllegalArgumentException("Preference type mismatch");
                    decoded.put(key,value);
                }
                SharedPreferences.Editor edit=prefs.edit();for(java.util.Map.Entry<String,Object> entry:decoded.entrySet()){String key=entry.getKey();Object v=entry.getValue();if(v instanceof Boolean)edit.putBoolean(key,(Boolean)v);else if(v instanceof Integer)edit.putInt(key,(Integer)v);else if(v instanceof Long)edit.putLong(key,(Long)v);else if(v instanceof Float)edit.putFloat(key,(Float)v);else if(v instanceof Set)edit.putStringSet(key,(Set<String>)v);else edit.putString(key,(String)v);}
                if(!edit.commit())throw new IllegalStateException("Backup could not be saved");trackIndex=prefs.getInt("last_track",0);if(trackIndex<0||trackIndex>=tracks.size())trackIndex=0;if(prefs.getBoolean("reminders_enabled",true))ReminderReceiver.scheduleNext(this);else ReminderReceiver.cancel(this);if(ttsReady)tts.setSpeechRate(prefs.getFloat("tts_rate",.9f));toast("تمت استعادة تقدمك ("+decoded.size()+" عنصر).");home();
            }catch(Exception e){toast("تعذر استيراد الملف. تأكد أنه ملف تصدير صالح من هذا التطبيق.");}
        }
    }
    private int weakCount(){int n=0;for(Track t:tracks)for(Item i:t.items)if(prefs.getInt("misses_"+i.id,0)>0)n++;return n;}
    private void showWeakWords(){
        ArrayList<Item> weak=new ArrayList<>();for(Track t:tracks)for(Item i:t.items)if(prefs.getInt("misses_"+i.id,0)>0)weak.add(i);Collections.sort(weak,(a,b)->Integer.compare(prefs.getInt("misses_"+b.id,0),prefs.getInt("misses_"+a.id,0)));screen="weak";setup("كلمات تحتاج تدريبًا",true,false);if(weak.isEmpty()){full(body,text("ممتاز! لا توجد كلمات متعثرة حاليًا. ستظهر هنا الكلمات التي تخطئ فيها.",17,GREEN,true));return;}full(body,text("الأكثر تكرارًا في الأخطاء تظهر أولًا.",14,MUTED,false));space(body,10);for(Item i:weak){LinearLayout c=card();full(c,text(i.es+"   •   "+i.ar,17,INK,true));full(c,text("أخطاء مسجلة: "+prefs.getInt("misses_"+i.id,0),13,MUTED,false));space(body,7);full(body,c);}Button practice=button("تدرب على الكلمات المتعثرة",GREEN,Color.WHITE);full(body,practice);practice.setOnClickListener(v->{if(weak.size()>PER_LESSON)weak.subList(PER_LESSON,weak.size()).clear();reviewMode=true;intro(weak);});
    }
    private Item findPhrase(String phrase){for(Track t:tracks)for(Item i:t.items)if(LearningLogic.normalize(i.es).equals(LearningLogic.normalize(phrase)))return i;return new Item("dialogue_"+phrase.hashCode(),phrase,"");}
    private void showScenarios(){
        screen="scenarios";setup("محادثات الحياة اليومية 🎭",true,false);
        full(body,text("اختر موقفًا من مواقف إسبانيا الحقيقية: هتسمع جملة الرد، وتردّ بإسبانيتك وتصحيح تلقائي كل دور.",15,MUTED,false));space(body,10);
        for(int s=0;s<RealLife.SCENARIOS.length;s++){
            final int scenario=s;String[] data=RealLife.SCENARIOS[scenario];LinearLayout c=card();
            full(c,text(data[0],19,INK,true));space(c,4);full(c,text(data[1],14,MUTED,false));space(c,8);
            full(c,text((data.length-2)/2+" أدوار في الحوار",13,GREEN,true));space(c,10);
            Button go=button("استعرض العبارات وتدرّب",GREEN,Color.WHITE);full(c,go);
            go.setOnClickListener(v->showScenarioDetails(scenario));
            full(body,c);space(body,10);
        }
    }
    private int openScenario=-1;
    private void showScenarioDetails(int scenario){
        openScenario=scenario;screen="scenario_detail";String[] data=RealLife.SCENARIOS[scenario];setup(data[0],true,false);
        full(body,text(data[1],16,MUTED,false));space(body,12);
        full(body,text("عباراتك في الموقف",21,INK,true));space(body,5);
        full(body,text("هذه عبارات المتعلم ومواقفها، وليست نصاً كاملاً لكل المتحدثين.",14,MUTED,false));space(body,12);
        for(int i=2;i+1<data.length;i+=2){Item item=findPhrase(data[i]);LinearLayout bubble=card();bubble.setBackground(bg(SKY,0,22));LinearLayout speaker=new LinearLayout(this);speaker.setGravity(Gravity.CENTER_VERTICAL);TextView avatar=text("أنت",13,DARK_GREEN,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(bg(PALE,0,22));speaker.addView(avatar,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView context=text(data[i+1],14,MUTED,false);LinearLayout.LayoutParams contextLayout=new LinearLayout.LayoutParams(0,-2,1);contextLayout.setMargins(dp(10),0,0,0);speaker.addView(context,contextLayout);full(bubble,speaker);space(bubble,10);TextView phrase=text(data[i],20,INK,true);phrase.setTextDirection(View.TEXT_DIRECTION_LTR);full(bubble,phrase);space(bubble,6);if(!item.ar.isEmpty()){TextView translation=text(item.ar,15,INK,false);translation.setVisibility(View.GONE);full(bubble,translation);Button toggle=button("إظهار الترجمة",Color.WHITE,BLUE);full(bubble,toggle);toggle.setOnClickListener(v->{boolean show=translation.getVisibility()!=View.VISIBLE;translation.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(show?"إخفاء الترجمة":"إظهار الترجمة");});}space(bubble,6);Button play=button("استمع للعبارة",Color.WHITE,BLUE);full(bubble,play);play.setOnClickListener(v->speak(item.es,false));full(body,bubble);space(body,10);}
        Button practice=button("ابدأ التدريب على الموقف",GREEN,Color.WHITE);full(body,practice);practice.setOnClickListener(v->startScenario(scenario));
    }
    private void startScenario(int scenario){
        String[] data=RealLife.SCENARIOS[scenario];int count=(data.length-2)/2;
        String[][] turns=new String[count][];
        for(int t=0;t<count;t++)turns[t]=new String[]{data[2+t*2],data[3+t*2],""};
        queue.clear();sessionItems.clear();mistakes.clear();skippedItems.clear();for(int n=0;n<turns.length;n++){Item item=findPhrase(turns[n][0]);Exercise ex=new Exercise(item,CONVERSE,false);ex.prompt=turns[n][1];queue.add(ex);sessionItems.add(item);}dialogueMode=true;dialogueTurn=0;cursor=0;correct=0;attempted=0;firstCorrect=0;firstAttempted=0;lessonStarted=System.currentTimeMillis();showExercise();
    }
    private void showDialogueSummary(){
        screen="summary";SharedPreferences.Editor edit=prefs.edit();edit.putInt("xp",prefs.getInt("xp",0)+correct);String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());int count=prefs.getString("daily_date","").equals(day)?prefs.getInt("daily_lessons",0):0;edit.putInt("total_first_correct",prefs.getInt("total_first_correct",0)+firstCorrect).putInt("total_first_attempted",prefs.getInt("total_first_attempted",0)+firstAttempted).putInt("acc_"+day+"_c",prefs.getInt("acc_"+day+"_c",0)+firstCorrect).putInt("acc_"+day+"_a",prefs.getInt("acc_"+day+"_a",0)+firstAttempted).putString("daily_date",day).putInt("daily_lessons",count+1).apply();dialogueMode=false;setup("الحوار اكتمل! 🎭",false,false);LinearLayout c=card();full(c,text("أحسنت! أكملت "+queue.size()+" أدوار في الحوار.",22,INK,true));space(c,8);full(c,text("✦ "+correct+" نقطة تدريب",17,GREEN,true));full(body,c);space(body,12);Button homeButton=button("العودة للرئيسية",GREEN,Color.WHITE);full(body,homeButton);homeButton.setOnClickListener(v->home());
    }
    private void begin(ArrayList<Item> items){
        dialogueMode=false;matchMoves=0;queue.clear();mistakes.clear();skippedItems.clear();sessionItems.clear();sessionItems.addAll(items);cursor=0;correct=0;attempted=0;firstCorrect=0;firstAttempted=0;speechSkipped=0;listeningSkipped=0;lessonStarted=System.currentTimeMillis();
        ArrayList<String> phrases=new ArrayList<>(), translations=new ArrayList<>();for(Item item:sessionItems){phrases.add(item.es);translations.add(item.ar);}
        for(LearningLogic.Step step:LearningLogic.lessonPlan(phrases,translations))queue.add(new Exercise(sessionItems.get(step.itemIndex),step.type,false));
        showExercise();
    }
    private ArrayList<Item> distractors(Item target,boolean arabic){
        ArrayList<Item> pool=new ArrayList<>(sessionItems);Collections.shuffle(pool);ArrayList<Item> fallback=new ArrayList<>();for(Track t:tracks)fallback.addAll(t.items);Collections.shuffle(fallback);pool.addAll(fallback);
        ArrayList<Item> out=new ArrayList<>();out.add(target);Set<String> seen=new HashSet<>();seen.add(arabic?target.ar.trim():LearningLogic.normalize(target.es));
        for(Item i:pool){String value=arabic?i.ar:i.es;String key=arabic?value.trim():LearningLogic.normalize(value);if(!key.isEmpty()&&seen.add(key))out.add(i);if(out.size()==4)break;}Collections.shuffle(out);return out;
    }
    private void audioControls(LinearLayout box,String value){
        LinearLayout row=new LinearLayout(this);Button normal=button("🔊 استمع",SKY,BLUE),slow=button("🐢 ببطء",Color.WHITE,BLUE);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);a.setMargins(0,0,dp(6),0);row.addView(normal,a);row.addView(slow,new LinearLayout.LayoutParams(0,-2,1));full(box,row);normal.setOnClickListener(v->speak(value,false));slow.setOnClickListener(v->speak(value,true));
    }
    private String conversationPrompt(Item item){String answer=LearningLogic.normalize(item.es);if(answer.contains("hola")||answer.contains("buenos dias")||answer.contains("buenas tardes")||answer.contains("buenas noches"))return "وصلتَ إلى مقهى في مدريد. ابدأ بتحية الموظف.";if(answer.contains("quiero")||answer.contains("me gustaria")||answer.contains("cafe")||answer.contains("agua")||answer.contains("comida"))return "أنت في مقهى إسباني. اطلب ما تراه في الجملة.";if(answer.contains("cuanto")||answer.contains("precio")||answer.contains("cuesta"))return "اسأل البائع عن السعر مستخدمًا العبارة.";if(answer.contains("donde")||answer.contains("calle")||answer.contains("estacion"))return "أنت سائح وتبحث عن مكان قريب. اسأل أحد المارة.";if(answer.contains("gracias")||answer.contains("por favor")||answer.contains("perdon"))return "أكمل حوارًا مهذبًا مستخدمًا العبارة المناسبة.";return "موقف يومي: استخدم العبارة الإسبانية التالية في حديثك.";}
    private void showExercise(){
        stopRecognizer();if(cursor>=queue.size()){finishLesson();return;}screen="exercise";answered=false;selectedId=null;selectedWord=null;selectedWords.clear();selectedChips.clear();input=null;action=null;
        Exercise ex=queue.get(cursor);setup((dialogueMode?"موقف • ":"سؤال ")+(cursor+1)+" / "+queue.size(),true,true);full(body,progress(cursor,queue.size()));space(body,16);
        String title=ex.type==INTRO?"كلمة أو تعبير جديد ✨":ex.type==MEANING?"اختر المعنى الصحيح":ex.type==REVERSE?"اختر الترجمة الإسبانية":ex.type==LISTEN?"اسمع واختر الإسباني":ex.type==ORDER?"رتّب الجملة بالإسباني":ex.type==MISSING?"كمّل الكلمة الناقصة":ex.type==MATCH?"طابق البطاقات 🃏":ex.type==CONVERSE?"موقف من الحياة اليومية 🎭":ex.type==TRANSFORM?"حوّل الجملة إلى المؤنث ♀":ex.type==TYPE?"اكتب بالإسباني":"قولها بالإسباني";
        full(body,text(ex.retry?"مراجعة خطأ سابق  ↻":title,23,INK,true));if(ex.retry){space(body,4);full(body,text(title,15,MUTED,false));}space(body,12);
        LinearLayout prompt=card();
        if(ex.type==INTRO||ex.type==MEANING||ex.type==SPEAK){TextView es=text(ex.item.es,23,INK,true);es.setTextDirection(View.TEXT_DIRECTION_LTR);full(prompt,es);if(ex.type==INTRO||ex.type==SPEAK){space(prompt,7);full(prompt,text(ex.item.ar,16,MUTED,false));}space(prompt,12);audioControls(prompt,ex.item.es);}
        else if(ex.type==LISTEN){full(prompt,text("اسمع كويس، وبعدها اختار اللي سمعته.",16,INK,true));space(prompt,12);audioControls(prompt,ex.item.es);}
        else if(ex.type==MISSING){TextView blank=text(LearningLogic.sentenceWithBlank(ex.item.es),22,INK,true);blank.setTextDirection(View.TEXT_DIRECTION_LTR);full(prompt,blank);space(prompt,8);full(prompt,text(ex.item.ar,15,MUTED,false));}
        else if(ex.type==MATCH){full(prompt,text("اقلب البطاقات ووصّل كل كلمة إسبانية بمعناها العربي.",16,INK,true));space(prompt,8);mascot(prompt,"ركز على المعاني—تقدر تعملها!");}
        else if(ex.type==CONVERSE){if(ex.prompt.isEmpty())ex.prompt=conversationPrompt(ex.item);full(prompt,text(ex.prompt,17,INK,true));space(prompt,8);mascot(prompt,dialogueMode?"دورك في الحوار—اكتب ردك بالإسبانية.":"تخيل الموقف واتكلم بثقة!");space(prompt,8);TextView phrase=text(ex.item.es,21,BLUE,true);phrase.setTextDirection(View.TEXT_DIRECTION_LTR);full(prompt,phrase);space(prompt,7);if(!ex.item.ar.isEmpty())full(prompt,text(ex.item.ar,15,MUTED,false));audioControls(prompt,ex.item.es);}
        else if(ex.type==TRANSFORM){TextView base=text(LearningLogic.maleVariant(ex.item.es),22,INK,true);base.setTextDirection(View.TEXT_DIRECTION_LTR);full(prompt,base);space(prompt,8);full(prompt,text(ex.item.ar,15,MUTED,false));space(prompt,8);full(prompt,text("اكتب نفس الجملة بصيغة المؤنث ♀",15,BLUE,true));}
        else{full(prompt,text(ex.item.ar,20,INK,true));space(prompt,7);full(prompt,text(ex.type==ORDER?"اضغط الكلمات بالترتيب الصحيح.":ex.type==REVERSE?"اختار المقابل بالإسباني.":"اكتب الإجابة بدون مساعدة.",14,MUTED,false));}
        prompt.setBackground(bg(ex.type==INTRO?PALE:ex.type==LISTEN||ex.type==CONVERSE?SKY:Color.WHITE,0,24));full(body,prompt);space(body,16);answers=column();full(body,answers);
        if(ex.type==INTRO){full(answers,text("اسمع واقرأ المعنى، وبعدها جرّب بنفسك.",15,MUTED,false));}
        else if(ex.type==MEANING||ex.type==LISTEN||ex.type==REVERSE)showChoices(ex);
        else if(ex.type==ORDER)showWordBank(ex);
        else if(ex.type==MISSING)showMissingChoices(ex);
        else if(ex.type==MATCH){showMatchCards();space(answers,8);full(answers,text("الأزواج المطابقة تظل مكشوفة ✓",13,MUTED,false));}
        else if(ex.type==CONVERSE){if(dialogueMode){input=new EditText(this);input.setTypeface(nunitoRegular);input.setSingleLine(false);input.setTextSize(19);input.setTextColor(foreground(INK));input.setHintTextColor(foreground(MUTED));input.setHint("اكتب ردك بالإسباني");input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setPadding(dp(15),dp(12),dp(15),dp(12));input.setBackground(bg(Color.WHITE,LINE,16));full(answers,input);}else showConversationOptions(ex);}
        else if(ex.type==TYPE||ex.type==TRANSFORM){input=new EditText(this);input.setTypeface(nunitoRegular);input.setSingleLine(false);input.setTextSize(19);input.setTextColor(foreground(INK));input.setHintTextColor(foreground(MUTED));input.setHint("اكتب هنا بالإسباني");input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setPadding(dp(15),dp(12),dp(15),dp(12));input.setBackground(bg(Color.WHITE,LINE,16));full(answers,input);}
        else {Button mic=button("🎙  اضغط وتكلم",BLUE,Color.WHITE);full(answers,mic);mic.setOnClickListener(v->{if(!answered)requestSpeech();});space(answers,8);Button skip=button("مش قادر أتكلم دلوقتي",Color.WHITE,MUTED);full(answers,skip);skip.setOnClickListener(v->skipExercise(true));}
        if(ex.type==LISTEN){space(answers,7);Button skip=button("الصوت مش متاح؟ تخطي",Color.WHITE,MUTED);full(answers,skip);skip.setOnClickListener(v->skipExercise(false));}
        space(answers,10);Button lookup=button("البحث في القاموس",Color.WHITE,BLUE);full(answers,lookup);lookup.setOnClickListener(v->showDictionaryDialog());
        feedback=text("",15,INK,true);feedback.setVisibility(View.GONE);feedback.setPadding(dp(14),dp(12),dp(14),dp(12));full(footer,feedback);space(footer,8);
        action=button(ex.type==INTRO?"فهمت، كمّل":ex.type==SPEAK?"اضغط الميكروفون للتحدث":ex.type==MATCH?"طابق كل البطاقات أولًا":"تحقق",GREEN,Color.WHITE);action.setEnabled(ex.type==INTRO);full(footer,action);if(input!=null)watchAnswerInput(input);
        action.setOnClickListener(v->{if(answered||ex.type==INTRO){cursor++;showExercise();}else checkAnswer();});
    }
    private void showDictionaryDialog(){
        LinearLayout panel=column();panel.setPadding(dp(16),dp(8),dp(16),dp(8));EditText query=new EditText(this);query.setTypeface(cairoRegular);query.setSingleLine(true);query.setTextColor(foreground(INK));query.setHintTextColor(foreground(MUTED));query.setHint("ابحث بالعربية أو الإسبانية");full(panel,query);
        ScrollView resultsScroll=new ScrollView(this);LinearLayout results=column();resultsScroll.addView(results);panel.addView(resultsScroll,new LinearLayout.LayoutParams(-1,dp(280)));
        Runnable render=()->{results.removeAllViews();String needle=query.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;if(needle.isEmpty()){full(results,text("اكتب كلمة للبحث في المنهج.",15,MUTED,false));return;}for(Track track:tracks)for(Item item:track.items){if(count>=20)break;if(item.es.toLowerCase(Locale.ROOT).contains(needle)||item.ar.contains(needle)){Button row=button(item.es+" — "+item.ar,Color.WHITE,INK);row.setOnClickListener(v->speak(item.es,false));full(results,row);count++;}}if(count==0)full(results,text("لا توجد نتائج مطابقة.",15,MUTED,false));};
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){render.run();}public void afterTextChanged(android.text.Editable s){}});render.run();new android.app.AlertDialog.Builder(this).setTitle("القاموس — اضغط النتيجة للاستماع").setView(panel).setPositiveButton("عودة للدرس",null).show();
    }
    private void watchAnswerInput(EditText field){
        field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(action!=null&&!answered)action.setEnabled(!s.toString().trim().isEmpty());}public void afterTextChanged(android.text.Editable s){}});
    }
    private void showChoices(Exercise ex){
        for(Item candidate:distractors(ex.item,ex.type==MEANING)){
            String label=ex.type==MEANING?candidate.ar:candidate.es;Button b=button(label,Color.WHITE,INK);b.setMinHeight(dp(55));if(ex.type==LISTEN||ex.type==REVERSE)b.setTextDirection(View.TEXT_DIRECTION_LTR);full(answers,b);space(answers,7);
            b.setOnClickListener(v->{if(answered)return;selectedId=candidate.id;action.setEnabled(true);for(int i=0;i<answers.getChildCount();i++){View child=answers.getChildAt(i);if(child instanceof Button)child.setBackground(bg(Color.WHITE,LINE,16));}b.setBackground(bg(SKY,BLUE,16));});
        }
    }
    private void showConversationOptions(Exercise ex){
        String[] options={"أقول العبارة بصوتي 🎙","أكتب العبارة بالإسبانية ✍","أستمع للنطق مرة أخرى 🔊"};
        for(int i=0;i<options.length;i++){final int choice=i;Button option=button(options[i],Color.WHITE,INK);full(answers,option);space(answers,7);option.setOnClickListener(v->{if(answered)return;for(int n=0;n<answers.getChildCount();n++)if(answers.getChildAt(n) instanceof Button)((Button)answers.getChildAt(n)).setBackground(bg(Color.WHITE,LINE,16));option.setBackground(bg(SKY,BLUE,16));if(choice==0){requestSpeech();}else if(choice==1){if(input==null){input=new EditText(this);input.setTypeface(nunitoRegular);input.setSingleLine(false);input.setTextSize(19);input.setTextColor(foreground(INK));input.setHintTextColor(foreground(MUTED));input.setHint("اكتب ردك بالإسباني");input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setPadding(dp(15),dp(12),dp(15),dp(12));input.setBackground(bg(Color.WHITE,LINE,16));answers.addView(input);watchAnswerInput(input);}action.setEnabled(!input.getText().toString().trim().isEmpty());input.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);}else speak(ex.item.es,false);});}
    }
    private void showMatchCards(){
        ArrayList<Item> pairItems=new ArrayList<>();Set<String> spanishSeen=new HashSet<>(), arabicSeen=new HashSet<>();ArrayList<Item> candidates=new ArrayList<>(sessionItems);Collections.shuffle(candidates);
        for(Item item:candidates){String spanishKey=LearningLogic.normalize(item.es),arabicKey=item.ar.trim();if(spanishKey.isEmpty()||arabicKey.isEmpty()||!spanishSeen.add(spanishKey)||!arabicSeen.add(arabicKey))continue;pairItems.add(item);if(pairItems.size()==4)break;}
        if(pairItems.size()<3){answered=true;action.setEnabled(true);action.setText("متابعة");return;}
        ArrayList<String[]> pairs=new ArrayList<>();for(Item item:pairItems)pairs.add(new String[]{item.es,item.ar});final ArrayList<Item> matchedItems=pairItems;
        matchCards=new MatchCards(this,pairs,moves->{matchMoves=moves;answered=true;correct+=pairs.size();attempted+=pairs.size();firstCorrect+=pairs.size();firstAttempted+=pairs.size();for(Item item:matchedItems)mistakes.remove(item.id+":"+MATCH);feedback.setVisibility(View.VISIBLE);feedback.setTextColor(foreground(DARK_GREEN));feedback.setBackground(bg(PALE,0,14));feedback.setText("ممتاز! طابقت "+pairs.size()+" أزواج في "+moves+" محاولة.");action.setEnabled(true);action.setText("متابعة");});full(answers,matchCards);
    }
    private void showMissingChoices(Exercise ex){

        String correctWord=LearningLogic.missingWord(ex.item.es);ArrayList<String> options=new ArrayList<>();options.add(correctWord);Set<String> seen=new HashSet<>();seen.add(LearningLogic.normalize(correctWord));
        ArrayList<Item> pool=new ArrayList<>(sessionItems);for(Track t:tracks)pool.addAll(t.items);Collections.shuffle(pool);
        for(Item item:pool){if(!LearningLogic.canProduce(item.es))continue;ArrayList<String> words=new ArrayList<>(LearningLogic.words(item.es));Collections.shuffle(words);for(String word:words){String key=LearningLogic.normalize(word);if(key.length()>=2&&seen.add(key))options.add(word);if(options.size()>=4)break;}if(options.size()>=4)break;}
        Collections.shuffle(options);WordFlow choices=new WordFlow(this);full(answers,choices);
        for(String option:options){Button choice=button(option,Color.WHITE,INK);choices.addView(choice);choice.setOnClickListener(v->{if(answered)return;selectedWord=option;action.setEnabled(true);for(int i=0;i<choices.getChildCount();i++)choices.getChildAt(i).setBackground(bg(Color.WHITE,LINE,16));choice.setBackground(bg(SKY,BLUE,16));});}
    }
    private void showWordBank(Exercise ex){
        full(answers,text("إجابتك — اضغط كلمة لإزالتها",14,MUTED,false));space(answers,5);
        selectedBank=new WordFlow(this);selectedBank.setMinimumHeight(dp(55));selectedBank.setPadding(dp(5),dp(5),dp(5),dp(5));selectedBank.setBackground(bg(Color.WHITE,LINE,14));full(answers,selectedBank);space(answers,12);
        full(answers,text("اختار الكلمات بالترتيب",14,MUTED,false));space(answers,5);
        bank=new WordFlow(this);full(answers,bank);
        ArrayList<String> words=new ArrayList<>(LearningLogic.words(ex.item.es));Collections.shuffle(words);
        for(String word:words){Button chip=button(word,Color.WHITE,BLUE);chip.setMinHeight(dp(48));bank.addView(chip);chip.setOnClickListener(v->{if(answered||!chip.isEnabled())return;selectedWords.add(word);selectedChips.add(chip);chip.setEnabled(false);renderSelectedWords();});}
        space(answers,8);Button reset=button("↺  إعادة ترتيب",Color.WHITE,MUTED);full(answers,reset);reset.setOnClickListener(v->{if(answered)return;for(Button chip:selectedChips)chip.setEnabled(true);selectedWords.clear();selectedChips.clear();renderSelectedWords();});
    }
    private void renderSelectedWords(){
        if(action!=null&&!answered)action.setEnabled(!selectedWords.isEmpty());
        selectedBank.removeAllViews();
        for(int i=0;i<selectedWords.size();i++){final int index=i;Button chosen=button(selectedWords.get(i),SKY,BLUE);chosen.setMinHeight(dp(48));selectedBank.addView(chosen);chosen.setOnClickListener(v->{if(answered)return;selectedChips.remove(index).setEnabled(true);selectedWords.remove(index);renderSelectedWords();});}
    }
    private void checkAnswer(){
        Exercise ex=queue.get(cursor);
        if(ex.type==MEANING||ex.type==LISTEN||ex.type==REVERSE){if(selectedId==null){toast("اختار إجابة الأول.");return;}grade(selectedId.equals(ex.item.id),null);}
        else if(ex.type==MISSING){if(selectedWord==null){toast("اختار كلمة الأول.");return;}grade(LearningLogic.normalize(selectedWord).equals(LearningLogic.normalize(LearningLogic.missingWord(ex.item.es))),null);}
        else if(ex.type==ORDER){if(selectedWords.isEmpty()){toast("اضغط الكلمات بالترتيب الأول.");return;}grade(LearningLogic.matches(ex.item.es,android.text.TextUtils.join(" ",selectedWords)),null);}
        else if(ex.type==TYPE||ex.type==TRANSFORM||ex.type==CONVERSE){if(input==null){toast("اختار طريقة الرد الأول: تحدث أو اكتب.");return;}String value=input.getText().toString();if(value.trim().isEmpty()){toast("اكتب إجابتك الأول.");return;}((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);grade(LearningLogic.matches(ex.type==TRANSFORM?LearningLogic.femaleVariant(ex.item.es):ex.item.es,value),ex.type==CONVERSE?ex.prompt:null);}
    }
    private void skipExercise(boolean speech){
        if(answered)return;stopRecognizer();Exercise ex=queue.get(cursor);if(speech)speechSkipped++;else listeningSkipped++;skippedItems.add(ex.item.id);answered=true;
        feedback.setVisibility(View.VISIBLE);feedback.setTextColor(foreground(MUTED));feedback.setBackground(bg(SKY,0,14));feedback.setText("تم التخطي بدون تقييم. هنرجع للعنصر في المراجعة.");action.setEnabled(true);action.setText("متابعة");disableAnswers();
    }
    private void disableAnswers(){for(int i=0;i<answers.getChildCount();i++){View v=answers.getChildAt(i);v.setEnabled(false);if(v instanceof MatchCards)for(int j=0;j<((MatchCards)v).getChildCount();j++)((MatchCards)v).getChildAt(j).setEnabled(false);}}
    private void grade(boolean ok,String extra){
        if(answered||!screen.equals("exercise")||cursor>=queue.size())return;Exercise ex=queue.get(cursor);stopRecognizer();answered=true;attempted++;if(!ex.retry){firstAttempted++;if(ok)firstCorrect++;}if(ok){correct++;prefs.edit().putInt("misses_"+ex.item.id,Math.max(0,prefs.getInt("misses_"+ex.item.id,0)-1)).apply();}else{mistakes.add(ex.item.id+":"+ex.type);prefs.edit().putInt("misses_"+ex.item.id,prefs.getInt("misses_"+ex.item.id,0)+1).apply();queue.add(new Exercise(ex.item,ex.type,true));}
        disableAnswers();feedback.setVisibility(View.VISIBLE);feedback.setTextColor(foreground(ok?DARK_GREEN:RED));feedback.setBackground(bg(ok?PALE:RED_BG,0,14));feedback.setAlpha(0f);feedback.animate().alpha(1f).setDuration(180).start();
        String answer=ex.type==MEANING?ex.item.ar:ex.type==MISSING?LearningLogic.missingWord(ex.item.es):ex.type==TRANSFORM?LearningLogic.femaleVariant(ex.item.es):ex.item.es;feedback.setText((ok?"إجابة صحيحة! ✓":"مش مشكلة، الصحيح: "+answer+"\nهنرجع للسؤال في آخر الدرس.")+(extra==null?"":"\n"+extra));action.setEnabled(true);action.setText("متابعة");
    }
    private void requestSpeech(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){toast("خدمة التعرف على الكلام غير متاحة. تقدر تتخطى التمرين.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingSpeech=true;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);return;}listen();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==MIC_PERMISSION&&pendingSpeech){pendingSpeech=false;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&screen.equals("exercise")&&!answered)listen();else toast("إذن الميكروفون مطلوب للتحدث؛ يمكنك التخطي.");}if(requestCode==NOTIFY_PERMISSION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){ReminderReceiver.scheduleNext(this);toast("تم تفعيل التذكير اليومي.");}else toast("إذن الإشعارات مطلوب ليصلك التذكير اليومي.");showLearningSettings();}}
    private void stopRecognizer(){if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}}
    private void listen(){
        stopRecognizer();recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){feedback.setVisibility(View.VISIBLE);feedback.setTextColor(foreground(BLUE));feedback.setBackground(bg(SKY,0,14));feedback.setText("اتكلم دلوقتي بالإسباني...");}
            public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){feedback.setText("جاري فهم كلامك...");}
            public void onError(int error){if(!screen.equals("exercise")||answered)return;feedback.setVisibility(View.VISIBLE);feedback.setText("الصوت مش واضح أو الخدمة غير متاحة. جرّب تاني أو تخطَّ التمرين.");}
            public void onResults(Bundle results){if(!screen.equals("exercise")||answered)return;ArrayList<String> matches=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(matches==null||matches.isEmpty()){feedback.setText("ما سمعتش الكلام بوضوح، حاول تاني.");return;}
                String expected=queue.get(cursor).item.es;boolean match=false;for(String candidate:matches)if(LearningLogic.matches(expected,candidate)){match=true;break;}grade(match,"سمعت: "+matches.get(0));}
            public void onPartialResults(Bundle b){}public void onEvent(int e,Bundle b){}
        });Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-ES");intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);recognizer.startListening(intent);
    }
    private void finishLesson(){
        if(dialogueMode){showDialogueSummary();return;}
        screen="summary";SharedPreferences.Editor edit=prefs.edit();long now=System.currentTimeMillis();Set<String> touched=new HashSet<>();
        for(Exercise ex:queue)if(ex.type!=INTRO&&ex.type!=MATCH&&ex.type!=TRANSFORM&&touched.add(ex.item.id)){boolean wrong=false;for(String key:mistakes)if(key.startsWith(ex.item.id+":")){wrong=true;break;}
            boolean needsReview=wrong||skippedItems.contains(ex.item.id);float ease=prefs.getFloat("ease_"+ex.item.id,2.5f);long interval=prefs.getLong("interval_"+ex.item.id,0);
            if(needsReview){ease=Math.max(1.3f,ease-0.2f);interval=0;edit.putLong("due_"+ex.item.id,now+3600000L);}
            else{ease=Math.min(3.0f,ease+0.1f);interval=interval==0?86400000L:Math.min((long)(interval*ease),30L*86400000L);edit.putLong("due_"+ex.item.id,now+interval);}
            edit.putFloat("ease_"+ex.item.id,ease).putLong("interval_"+ex.item.id,interval);}
        if(!reviewMode&&lessonIndex==completed(trackIndex))edit.putInt("complete_"+trackIndex,lessonIndex+1);
        if(!reviewMode){String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(now));int count=prefs.getString("daily_date","").equals(day)?prefs.getInt("daily_lessons",0):0;edit.putString("daily_date",day).putInt("daily_lessons",count+1);}
        edit.putInt("total_first_correct",prefs.getInt("total_first_correct",0)+firstCorrect).putInt("total_first_attempted",prefs.getInt("total_first_attempted",0)+firstAttempted);
        String statDay=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(now));edit.putInt("acc_"+statDay+"_c",prefs.getInt("acc_"+statDay+"_c",0)+firstCorrect).putInt("acc_"+statDay+"_a",prefs.getInt("acc_"+statDay+"_a",0)+firstAttempted);
        int earned=10+correct;edit.putInt("xp",prefs.getInt("xp",0)+earned);
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.US);String today=date.format(new Date(now));Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.add(Calendar.DAY_OF_YEAR,-1);String yesterday=date.format(cal.getTime());String last=prefs.getString("last_day","");
        if(!today.equals(last))edit.putInt("streak",yesterday.equals(last)?prefs.getInt("streak",0)+1:1).putString("last_day",today);edit.apply();
        setup("أحسنت!",false,false);mascot(body,"خطوة جديدة في رحلتك!");space(body,14);LinearLayout c=card();c.setBackground(bg(PALE,0,24));full(c,text("درس جديد في رصيدك!",25,INK,true));space(c,10);
        full(c,text("✦  "+earned+" نقطة",19,GREEN,true));space(c,7);full(c,text("إجابات صحيحة من أول محاولة: "+firstCorrect+" من "+firstAttempted,16,INK,true));space(c,7);full(c,text("دقة أول محاولة: "+(firstAttempted==0?"—":percentage(firstCorrect,firstAttempted)),17,INK,true));space(c,7);
        for(Item item:sessionItems)full(c,text(item.es+"  —  "+item.ar,14,INK,false));space(c,7);
        long seconds=Math.max(1,(now-lessonStarted)/1000);full(c,text("الوقت: "+(seconds/60)+" دقيقة و"+(seconds%60)+" ثانية",15,MUTED,false));
        if(speechSkipped>0||listeningSkipped>0){space(c,8);full(c,text("تم تخطي "+speechSkipped+" نطق و"+listeningSkipped+" استماع؛ لم تُحسب في الدقة.",14,MUTED,false));}
        String goalDay=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(now));int goalDone=prefs.getString("daily_date","").equals(goalDay)?prefs.getInt("daily_lessons",0):0;int goal=prefs.getInt("daily_goal",1);space(c,8);full(c,text("هدف اليوم: "+goalDone+" من "+goal+" درس",14,INK,true));full(c,progress(Math.min(goalDone,goal),goal));
        space(c,8);full(c,text("العناصر اللي أخطأت فيها أو تخطيتها هتظهر بدري للمراجعة.",14,MUTED,false));full(c,text("مراجعة مستحقة الآن: "+dueCount()+" عنصر",14,BLUE,true));full(body,c);space(body,16);
        if(weakCount()>0){Button weak=button("راجع أخطائي",Color.WHITE,BLUE);full(body,weak);weak.setOnClickListener(v->showWeakWords());space(body,8);}
        if(!reviewMode&&completed(trackIndex)<tracks.get(trackIndex).lessons.size()){Button next=button("ابدأ الدرس التالي",GREEN,Color.WHITE);full(body,next);next.setOnClickListener(v->{lessonIndex=completed(trackIndex);reviewMode=false;intro(tracks.get(trackIndex).lessons.get(lessonIndex).items);});space(body,8);}
        Button path=button(reviewMode?"الرئيسية":"العودة للمسار",Color.WHITE,BLUE);full(body,path);path.setOnClickListener(v->{if(reviewMode)home();else path();});
    }
    @Override public boolean onKeyDown(int keyCode,android.view.KeyEvent event){if(Build.VERSION.SDK_INT<33&&keyCode==android.view.KeyEvent.KEYCODE_BACK&&event.getRepeatCount()==0){navigateBack();return true;}return super.onKeyDown(keyCode,event);}
    private void navigateBack(){stopRecognizer();if(screen.equals("home"))finish();else if(screen.equals("flashcard"))showReview();else if(screen.equals("word_detail"))showDictionary();else if(screen.equals("scenario_detail"))showScenarios();else if(screen.equals("path"))home();else if(screen.equals("intro")){if(reviewMode)home();else path();}else if(screen.equals("exercise")){toast("خرجت من الدرس بدون حفظ تقدمه.");if(reviewMode)home();else path();}else home();}
    @Override protected void onStop(){stopRecognizer();if(tts!=null)tts.stop();super.onStop();}
    @Override protected void onDestroy(){stopRecognizer();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
