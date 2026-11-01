from pathlib import Path
import json, re
root=Path(__file__).resolve().parent.parent
tracks=[]
for name, file, pattern in [
    ('الكلمات والتعبيرات','1000-كلمة-وجملة-إسبانية.md',r'^- (.+?) — (.+)$'),
    ('الجمل والمحادثة','1000-جملة-إسبانية-للمحادثة.md',r'^\d+\. (.+?) — (.+)$')]:
    content=(root/file).read_text(encoding='utf-8')
    units=[]
    for chunk in re.split(r'(?m)^## ',content)[1:]:
        lines=chunk.splitlines(); items=[]
        for line in lines:
            match=re.match(pattern,line)
            if match: items.append({'es':match.group(1).strip(),'ar':match.group(2).strip()})
        if items: units.append({'title':lines[0].strip(),'items':items})
    assert sum(len(u['items']) for u in units)==1000, file
    tracks.append({'title':name,'units':units})
output=root/'android-spanish/app/src/main/assets/content.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps({'tracks':tracks},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print('tracks:',len(tracks),'counts:',[sum(len(u['items']) for u in t['units']) for t in tracks], 'units:',[len(t['units']) for t in tracks])
