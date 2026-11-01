package com.habla.espanol;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class LearningLogic {
    private LearningLogic() {}
    static final int INTRO=0,MEANING=1,LISTEN=2,ORDER=3,TYPE=4,SPEAK=5,REVERSE=6,MISSING=7,MATCH=8,CONVERSE=9,TRANSFORM=10;
    static final class Step { final int itemIndex,type; Step(int itemIndex,int type){this.itemIndex=itemIndex;this.type=type;} }
    static List<Step> lessonPlan(List<String> phrases){return lessonPlan(phrases,phrases);}
    static List<Step> lessonPlan(List<String> phrases,List<String> translations){
        ArrayList<Step> result=new ArrayList<>();java.util.Set<String> esSeen=new java.util.HashSet<>(),arSeen=new java.util.HashSet<>();int matchAt=-1;
        for(int i=0;i<Math.min(phrases.size(),translations.size());i++){String es=normalize(phrases.get(i)),ar=translations.get(i).trim();if(!es.isEmpty()&&!ar.isEmpty()&&esSeen.add(es)&&arSeen.add(ar)&&esSeen.size()>=3){matchAt=i;break;}}
        for(int i=0;i<phrases.size();i++){if(i>0)result.add(new Step(i-1,MEANING));result.add(new Step(i,INTRO));result.add(new Step(i,LISTEN));String phrase=phrases.get(i);int count=words(phrase).size();result.add(new Step(i,canProduce(phrase)?(count>=2&&count<=12?ORDER:TYPE):REVERSE));if(canProduce(phrase)&&count>=3&&count<=12)result.add(new Step(i,MISSING));if(genderTransformable(phrase))result.add(new Step(i,TRANSFORM));result.add(new Step(i,SPEAK));if(i==matchAt)result.add(new Step(i,MATCH));if(i%2==1&&phrases.size()>=3)result.add(new Step(i,CONVERSE));}
        if(!phrases.isEmpty())result.add(new Step(phrases.size()-1,MEANING));return result;
    }
    static java.util.Set<String> legacyFavorites(String value){if(value==null||!value.startsWith("[")||!value.endsWith("]"))throw new IllegalArgumentException("Invalid legacy favorites");java.util.Set<String> result=new java.util.HashSet<>();String content=value.substring(1,value.length()-1).trim();if(content.isEmpty())return result;for(String part:content.split(",",-1)){String id=part.trim();if(!id.matches("[0-9]+_[0-9]+_[0-9]+"))throw new IllegalArgumentException("Invalid legacy favorite ID");result.add(id);}return result;}
    static boolean validPreferenceType(String key,Object value){if(key.equals("dictionary_favorites"))return value instanceof java.util.Set;if(key.equals("dark_mode")||key.equals("onboarding_done")||key.equals("reminders_enabled"))return value instanceof Boolean;if(key.equals("tts_rate")||key.startsWith("ease_"))return value instanceof Float;if(key.startsWith("due_")||key.startsWith("interval_"))return value instanceof Long;if(key.equals("daily_date")||key.equals("last_day"))return value instanceof String;if(key.equals("last_track")||key.equals("daily_goal")||key.equals("daily_lessons")||key.equals("streak")||key.equals("xp")||key.equals("total_first_correct")||key.equals("total_first_attempted")||key.startsWith("complete_")||key.startsWith("misses_")||key.startsWith("acc_"))return value instanceof Integer;return true;}
    static long flashcardInterval(long oldInterval,float ease,int quality){if(quality<0||quality>3)throw new IllegalArgumentException("Invalid rating");long day=86400000L;if(quality==0)return 0;long previous=Math.max(0,Math.min(oldInterval,30*day));if(quality==1)return day;if(quality==2)return Math.min(30*day,Math.max(day,(long)(previous*1.5f)));return Math.min(30*day,Math.max(2*day,(long)(previous*Math.max(1.3f,Math.min(3f,ease)))));}
    static int completedItems(List<Integer> lessonSizes,int completedLessons){int total=0;for(int i=0;i<Math.min(Math.max(0,completedLessons),lessonSizes.size());i++)total+=lessonSizes.get(i);return total;}
    static String normalize(String text){return Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}+","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();}
    static boolean canProduce(String text){return text!=null&&!text.contains("/")&&!text.contains("...")&&!text.contains("…")&&!text.contains("(")&&!text.contains("{")&&!text.contains("_")&&!normalize(text).isEmpty()&&text.trim().length()<=100;}
    static List<String> words(String text){ArrayList<String> result=new ArrayList<>();for(String word:text.trim().split("\\s+"))if(!word.isEmpty())result.add(word);return result;}
    static int missingWordIndex(String text){List<String> words=words(text);int best=-1,length=0;for(int i=0;i<words.size();i++){int candidate=normalize(words.get(i)).length();if(candidate>length){best=i;length=candidate;}}return best;}
    static String sentenceWithBlank(String text){List<String> words=words(text);int index=missingWordIndex(text);if(index>=0)words.set(index,"____");return String.join(" ",words);}
    static String missingWord(String text){List<String> words=words(text);int index=missingWordIndex(text);return index<0?"":words.get(index);}
    private static boolean matchTemplate(String expected,String actual){String[] parts=expected.split("\\.\\.\\.|…",-1);if(parts.length==1)return actual.equals(normalize(expected));StringBuilder pattern=new StringBuilder("^");for(int i=0;i<parts.length;i++){if(i>0)pattern.append("(.+?)");pattern.append(Pattern.quote(normalize(parts[i])));}java.util.regex.Matcher matcher=Pattern.compile(pattern.append("$").toString()).matcher(actual);if(!matcher.matches())return false;for(int i=1;i<=matcher.groupCount();i++)if(matcher.group(i).replaceAll("[^a-z0-9]","").isEmpty())return false;return true;}
    static boolean matches(String expected,String actual){String clean=expected.replaceAll("\\([^)]*\\)","");String male=clean.replaceAll("/as\\b","").replaceAll("/a\\b","");String female=clean.replaceAll("os/as\\b","as").replaceAll("o/a\\b","a");String got=normalize(actual);return !got.isEmpty()&&(matchTemplate(male,got)||matchTemplate(female,got));}
    static String maleVariant(String text){String clean=text.replaceAll("\\([^)]*\\)","");return clean.replaceAll("/as\\b","").replaceAll("/a\\b","").trim();}
    static String femaleVariant(String text){String clean=text.replaceAll("\\([^)]*\\)","");return clean.replaceAll("os/as\\b","as").replaceAll("o/a\\b","a").trim();}
    static boolean genderTransformable(String text){if(text==null)return false;String clean=text.replaceAll("\\([^)]*\\)","");if(clean.contains("...")||clean.contains("…"))return false;String male=normalize(maleVariant(clean)),female=normalize(femaleVariant(clean));return !male.isEmpty()&&!female.isEmpty()&&!male.equals(female);}
}
