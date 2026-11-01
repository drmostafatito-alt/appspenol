package com.habla.espanol;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class MatchCards extends GridLayout {
    interface Listener { void onComplete(int moves); }
    private static final int GREEN=Color.rgb(16,145,105), BLUE=Color.rgb(53,102,207), INK=Color.rgb(28,47,74), LINE=Color.rgb(219,230,235), PALE=Color.rgb(232,248,240), SKY=Color.rgb(232,242,255);
    private static final class Card { final int pair; final boolean spanish; final String label; Button view; boolean matched;
        Card(int pair,boolean spanish,String label){this.pair=pair;this.spanish=spanish;this.label=label;} }
    private final ArrayList<Card> cards=new ArrayList<>(); private final Listener listener; private final boolean dark; private Card selected; private int matches,moves; private boolean locked,reported;
    MatchCards(Context context,List<String[]> pairs,Listener listener){super(context);this.listener=listener;this.dark=context.getSharedPreferences("habla_progress",Context.MODE_PRIVATE).getBoolean("dark_mode",false);setColumnCount(2);setUseDefaultMargins(true);setAlignmentMode(ALIGN_BOUNDS);
        for(int i=0;i<pairs.size();i++){cards.add(new Card(i,true,pairs.get(i)[0]));cards.add(new Card(i,false,pairs.get(i)[1]));}Collections.shuffle(cards,new Random(System.nanoTime()));
        for(Card card:cards){Button b=new Button(context);card.view=b;b.setAllCaps(false);b.setTextSize(16);b.setGravity(Gravity.CENTER);b.setPadding(8,10,8,10);b.setMinHeight((int)(64*getResources().getDisplayMetrics().density));b.setBackground(shape(Color.WHITE,LINE));b.setText("✦  بطاقة");b.setTextColor(fg(BLUE));b.setOnClickListener(v->choose(card));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=GridLayout.LayoutParams.WRAP_CONTENT;lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,GridLayout.FILL,1f);lp.setMargins(4,4,4,4);addView(b,lp);}}
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){int width=MeasureSpec.getSize(widthMeasureSpec);int cell=Math.max(0,(width-getPaddingLeft()-getPaddingRight())/2-18);int totalHeight=getPaddingTop()+getPaddingBottom();int rowHeight=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);measureChild(child,MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),heightMeasureSpec);rowHeight=Math.max(rowHeight,child.getMeasuredHeight());if(i%2==1){totalHeight+=rowHeight+8;rowHeight=0;}}if(getChildCount()%2!=0)totalHeight+=rowHeight+8;setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(totalHeight,heightMeasureSpec));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int width=getWidth()-getPaddingLeft()-getPaddingRight();int cell=Math.max(0,(width-18)/2);int y=getPaddingTop(),rowHeight=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);int x=getPaddingLeft()+(i%2)*(cell+18);child.layout(x,y,x+cell,y+child.getMeasuredHeight());rowHeight=Math.max(rowHeight,child.getMeasuredHeight());if(i%2==1){y+=rowHeight+8;rowHeight=0;}}}
    private int fg(int color){if(!dark)return color;if(color==INK)return 0xffeff5ef;if(color==BLUE)return 0xffa9c7ff;return color;}
    private int surface(int color){if(!dark)return color;if(color==Color.WHITE)return 0xff25332d;if(color==LINE)return 0xff42554a;if(color==SKY)return 0xff263750;if(color==PALE)return 0xff203e2e;return color;}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(surface(color));d.setCornerRadius(24);d.setStroke(2,surface(stroke));return d;}
    private void reveal(Card card){card.view.setText(card.label);card.view.setTextColor(fg(INK));card.view.setBackground(shape(card.spanish?SKY:PALE,card.spanish?BLUE:GREEN));}
    private void choose(Card card){if(locked||card.matched||card==selected)return;reveal(card);if(selected==null){selected=card;return;}moves++;Card first=selected;selected=null;
        if(first.pair==card.pair&&first.spanish!=card.spanish){first.matched=card.matched=true;first.view.setText("✓  "+first.label);card.view.setText("✓  "+card.label);matches++;if(matches==cards.size()/2&&!reported){reported=true;postDelayed(()->listener.onComplete(moves),450);}return;}
        locked=true;postDelayed(()->{first.view.setText("✦  بطاقة");card.view.setText("✦  بطاقة");first.view.setTextColor(fg(BLUE));card.view.setTextColor(fg(BLUE));first.view.setBackground(shape(Color.WHITE,LINE));card.view.setBackground(shape(Color.WHITE,LINE));locked=false;},850);
    }
    int moveCount(){return moves;}
}
