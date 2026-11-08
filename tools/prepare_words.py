"""Legacy ECDICT builder for old releases.

WordHalo 0.22 and later use prepare_branded_books.py so each concrete book
keeps its verified membership and source order.
"""
import csv,re,json,sys,pathlib,hashlib,collections
if len(sys.argv)<2:raise SystemExit('Legacy builder. Use prepare_branded_books.py for v0.22+ assets.')
source=pathlib.Path(sys.argv[1]); dest=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets'
# Optional exact user word list: one English headword per line. Never fill to a
# desired number with unrelated words, and fail rather than silently omit entries.
requested=None
if len(sys.argv)>2:
 requested=list(dict.fromkeys(x.strip().lower() for x in pathlib.Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines() if x.strip()))
 if len(requested)!=2551:raise ValueError('The requested edition must have 2551 distinct headwords; found '+str(len(requested)))
dest.mkdir(parents=True,exist_ok=True)
# Hand-authored visual associations. These are illustrations, not etymologies.
groups={
 'sprout':('幼苗向上生长','cultivate nurture flourish thrive sprout germinate vegetation botanical fertile fertility bloom blossom'),
 'lamp':('灯亮，照亮四周','illuminate illumination luminous enlighten enlightenment radiate radiant brilliance glow glitter'),
 'wall':('墙挡住去路','barrier obstacle obstruct obstruction blockade hinder restrain restraint prohibit prohibition restrict restriction'),
 'wave':('曲线上下起伏','fluctuate fluctuation oscillate oscillation vary variation alternate turbulent turbulence frequency vibration vibrate'),
 'stack':('一枚枚积累起来','accumulate accumulation aggregate abundance abundant surplus cumulative compile compilation pile heap deposit'),
 'lens':('放大镜下看细节','scrutinize scrutiny inspect inspection investigate investigation magnify magnification perceive perception discern identify identification'),
 'planet':('沿轨道围绕星球','orbit orbital satellite astronomy astronomical cosmic cosmos universe universal celestial planet planetary'),
 'glass':('杯壁出现裂纹','fragile brittle fracture crack shatter vulnerability vulnerable delicate'),
 'bridge':('桥连接两端','bridge connect connection link linkage integrate integration reconcile reconciliation alliance associate association cooperate cooperation'),
 'scale':('天平两端保持平衡','equilibrium balance equality equivalent equal impartial justice justify compensate compensation offset'),
 'arrow':('向前推进，速度增加','accelerate acceleration propel propulsion advance advancement momentum rapid velocity speed expedite boost'),
 'shield':('盾牌挡住冲击','defend defense defence defensive protect protection safeguard secure security immune immunity shelter shield'),
 'mountain':('越过山峰，抵达目标','summit peak altitude ascend ascent climb surmount overcome conquer conquest accomplish accomplishment attain attainment'),
 'drop':('水滴汇入水面','moist moisture humid humidity liquid fluid reservoir irrigate irrigation drought evaporate evaporation condense condensation'),
 'magnet':('两端吸引到一起','attract attraction magnetic magnet magnetism appeal appealing adhere adherence cohesion coherent coherence'),
 'clock':('指针走过一段时间','duration interval temporary transient perpetual eternal eternity postpone delay punctual punctuality chronological chronology'),
 'branch':('一条路径分成两支','diverge divergence diverse diversity diversify alternative option separate separation distribute distribution classify classification'),
 'cycle':('循环往复的箭头','recycle circulation circulate recurrent recur renewable renew restore restoration recover recovery regenerate regeneration')
}
art={word:(scene,hint) for scene,(hint,words) in groups.items() for word in words.split()}
def brief(raw):
 lines=raw.replace('\\n','\n').splitlines()
 # Prefer common senses and discard technical domain/gloss annotations on a tiny screen.
 useful=[x.strip() for x in lines if x.strip() and not x.lstrip().startswith('[')]
 if not useful: useful=lines
 pos=''; pieces=[]
 for line in useful:
  m=re.match(r'^([a-z./ &]+\.)\s*',line)
  if m and not pos: pos=m.group(1).replace(' ','')
  line=re.sub(r'^[a-z./ &]+\.\s*','',line)
  line=re.sub(r'\([^)]*\)|（[^）]*）|\[[^]]*\]|【[^】]*】','',line)
  line=line.replace('…','').replace('...','').strip(' =；;，,')
  for p in re.split('[；;，,\n]',line):
   p=p.strip(' .。:：')
   if not re.search('[\u4e00-\u9fff]',p) or len(p)>18 or p in pieces:continue
   if sum(map(len,pieces))+len(p)+len(pieces)<=24: pieces.append(p)
   if len(pieces)>=3:break
  if len(pieces)>=2:break
 return '；'.join(pieces),pos or '词'
rows=[];seen=set(); excluded=[]
overrides=json.loads(pathlib.Path(__file__).with_name('meaning-overrides.json').read_text(encoding='utf-8'))
with source.open(encoding='utf-8-sig',newline='') as f:
 for r in csv.DictReader(f):
  tags=set(r['tag'].split()); bank=(1 if 'cet4' in tags else 0)|(2 if 'cet6' in tags else 0)
  word=r['word'].strip()
  if requested is None:
   if bank!=2:continue
  elif word not in requested:continue
  if not re.fullmatch(r"[a-z]+(?:[- '\u2019][a-z]+)*",word) or len(word)>36 or word in seen:continue
  bank=2
  meaning,pos=brief(r['translation'])
  if word in overrides:meaning,pos=overrides[word]
  # Keep whole sense fragments; never chop a Chinese definition mid-phrase.
  while len(meaning)>18 and '；' in meaning:meaning=meaning.rsplit('；',1)[0]
  pos={'a.':'adj.','ad.':'adv.','vt.':'v.','vi.':'v.','v.t.':'v.','v.i.':'v.'}.get(pos,pos)
  if not meaning:excluded.append(word);continue
  seen.add(word);scene,hint=art.get(word,('',''))
  rank=int(r['frq'] or 0) or int(r['bnc'] or 0) or 1000000
  rows.append((rank,word,meaning,pos,bank,scene,hint))
rows.sort(key=lambda x:(x[0],x[1]))
if requested is not None:
 missing=set(requested)-{x[1] for x in rows}
 if missing:raise ValueError('Definitions missing; nothing was overwritten: '+', '.join(sorted(missing)))
 rows.sort(key=lambda x:requested.index(x[1]))
with (dest/'words.tsv').open('w',encoding='utf-8',newline='\n') as f:
 for _,*values in rows:f.write('\t'.join(map(str,values))+'\n')
report={'source':'https://github.com/skywind3000/ECDICT','license':'MIT','source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'total':len(rows),'cet4':sum(bool(x[4]&1) for x in rows),'cet6':sum(bool(x[4]&2) for x in rows),'cet6_extension':sum(x[4]==2 for x in rows),'illustrated':sum(bool(x[5]) for x in rows),'hand_edited_meanings':sum(x[1] in overrides for x in rows),'excluded_no_short_meaning':excluded,'note':'考试标记来自 ECDICT，不声称为官方完整大纲；短释义用于快速记忆，省略例句和部分义项。'}
report['wordlist_status']='user_supplied_2551' if requested else 'provisional_ecdict_cet6_extension_not_baicizhan_2551'
(dest/'vocabulary-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
