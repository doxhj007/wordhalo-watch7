"""Build curated Commons photo associations. No arbitrary image search runs on the watch.
Run from the workspace root. Inspect the contact sheets before shipping new photos.
Photos retain their own licenses; attribution is embedded in scenes.json and shown in-app.
"""
import pathlib,json,urllib.request,urllib.parse,urllib.error,concurrent.futures,re,html,time,io,csv,sys,threading
from PIL import Image,ImageDraw
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
ROOT=pathlib.Path(__file__).resolve().parents[1]
A=ROOT/'app/src/main/assets'; W=pathlib.Path('work/watch-media'); W.mkdir(parents=True,exist_ok=True)
# Scene | Commons search | scene caption | exact headwords, reviewed as associations.
DATA='''forest|forest stream landscape|森林、植被与生态环境|environmental landscape ecosystem ecology vegetation rainforest preservation conserve conservation
court|courtroom interior|法庭里的诉讼、证言与裁决|attorney lawsuit testimony testify defendant verdict conviction sue allegation plead penalty proceeding judicial convict fraud theft warrant detain allege legitimate violation violate magistrate
meeting|business meeting conference table|围桌讨论、协商与合作的场景|client negotiation negotiate consultant collaboration consensus coalition alliance cooperative counterpart diplomatic consultation participant corporate enterprise analyst assessment evaluation presentation representation discourse competence peer ally engagement bureaucracy expertise mediate intervention intervene
hospital|hospital ward beds|用医院场景联想健康与诊疗|clinic clinical therapy symptom diagnose diagnostic physician ward prescription chronic complication prevention pregnancy pregnant maternity kidney spinal immune immunity infectious epidemic bacterium outbreak nutrition humanitarian paralyze paralysis paralyse fracture
chip|microprocessor silicon wafer|芯片表面的微型电路|chip microprocessor silicon sensor semiconductor electron transistor circuitry
camera|digital camera lens|镜头记录影像|photography photographer shutter photographic
guitar|acoustic guitar instrument|拨动琴弦，弹奏音乐|guitar chord instrumental melody solo acoustic
galaxy|Andromeda galaxy|浩瀚宇宙中的星系|galaxy cosmic cosmos astronomy astronomical aerospace
money|euro banknotes coins|用货币场景联想金融与收支|currency monetary earnings funding deficit mortgage transaction premium banker compensation financial inflation recession bankruptcy bankrupt bonus allowance repay tariff asset invoice reimburse unpaid millionaire treasurer
library|library books shelves|成排书架里的知识与文献|literacy manuscript thesis bibliography scholarly intellectual intellect curriculum instructor historian historic chronicle glossary handbook prose narrative narration
lab|laboratory microscope|用实验仪器联想观察与研究|microscopic empirical hypothesis hypotheses experimentation parameter scientific laboratory molecular genetics analytic analytical formulate quantitative qualitative coefficient synthesis synthesize
mountain|mountain summit landscape|崎岖地形与登山|terrain mountainous hike expedition summit lofty elevation
warehouse|warehouse pallets shelves|货架上储存和流通的货物|warehouse inventory merchandise shipment logistics stock storage commodity dealer
city|city skyscrapers skyline|摩天楼组成的都市天际线|metropolitan skyscraper urban municipal residential infrastructure
church|cathedral interior nave|教堂的宗教仪式场景|catholic bishop pope sermon hymn scripture ritual nave
ship|container ship sea|海上航行与港口运输|maritime vessel harbor harbour cargo tanker shipping barge embark ashore
bridge|suspension bridge river|桥梁跨越两岸|suspension span crossing traverse
storm|hurricane satellite eye|风暴的巨大旋涡|hurricane catastrophe disastrous destructive
volcano|volcano eruption lava|火山喷发的炽热熔岩|erupt eruption volcanic lava
desert|sand dunes desert|干旱、荒凉的沙漠|barren desolate arid dune
ice|Arctic iceberg|寒冷的极地冰雪|arctic antarctic polar icy glacier
coral|coral reef underwater|海底珊瑚礁与生态|reef coral marine
light|light bulb glowing|灯光照亮黑暗|illuminate luminous radiant brightness incandescent glow gleam fluorescent
bamboo|bamboo forest green|一节一节生长的竹子|bamboo
oak|oak tree green|橡树的叶子与橡子|oak
maple|maple leaves autumn|秋天的枫叶|maple
flower|tulip flowers garden|鲜艳的郁金香|tulip ornamental decorative gorgeous aesthetic
orchard|apple orchard trees|果园里整齐排列的果树|orchard grove cultivation
farm|sheep pasture grass|牧场上的放牧生活|pasture ranch graze grassy rural
bird|peacock feathers|孔雀展开华丽尾羽|peacock
snail|snail shell macro|蜗牛背着螺旋形外壳|snail spiral
beetle|beetle macro insect|甲虫与昆虫的外形|beetle insect pest
frog|common toad animal|蟾蜍粗糙的皮肤|toad amphibian
turtle|sea turtle underwater|海龟在水中游动|turtle reptile
shark|great white shark underwater|鲨鱼在海中捕食|shark predator prey
gorilla|gorilla animal portrait|大猩猩的面部与身体|gorilla primate mammal
leopard|leopard animal tree|有斑点的豹子|leopard panther feline
puppy|golden retriever puppy|幼犬与家养宠物|puppy hound canine
salmon|salmon fish food|三文鱼的橙红色鱼肉|salmon
crab|crab animal shore|螃蟹的螯与甲壳|crab
shrimp|shrimp seafood|虾的弯曲外形|shrimp
oyster|oyster shell food|牡蛎的贝壳与软体|oyster shellfish
garlic|garlic bulbs cloves|蒜头由一瓣瓣蒜组成|garlic
ginger|ginger root|姜的根茎|ginger spice
pumpkin|orange pumpkin|橙色南瓜|pumpkin
pineapple|pineapple fruit|带刺外皮的菠萝|pineapple
olive|olives olive branch|橄榄与枝叶|olive
walnut|walnuts shell|坚硬外壳里的核桃|walnut
bacon|bacon strips cooked|煎过的培根肉片|bacon ham
dessert|chocolate cake dessert|餐后的甜点|dessert sweetness snack
coffee|coffee cup foam|杯面上的泡沫|foam froth
ceramic|porcelain ceramic bowl|陶瓷器物与细腻釉面|ceramic porcelain earthenware pottery
weave|weaving loom textile|织机把线织成布|weaver textile fabric seam embroidery garment
sofa|sofa living room|客厅里的沙发|sofa couch lounge
stairs|spiral staircase interior|盘旋而上的楼梯|stairway ascend ascent descent
train|commuter train railway|日常通勤与轨道交通|commute transit railway commuter
boat|sailing yacht sea|帆船在海上航行|yacht sail cruise
parachute|parachute skydiving|张开的降落伞|parachute descend
windmill|windmill landscape|风推动叶片转动|windmill revolve rotor
gear|gear cogwheel mechanism|齿轮传递机械运动|mechanism torque kinetic mechanical dynamical
magnet|magnetic field iron filings|铁屑呈现磁场形状|magnetism polarity magnetic
prism|prism light spectrum|棱镜将光分解成光谱|prism spectrum refraction
water|water ripple circles|涟漪一圈圈向外扩散|ripple ripple wave radial concentric
fossil|ammonite fossil|岩石保存古生物痕迹|fossil prehistoric extinct extinction
castle|medieval castle fortress|中世纪城堡与防御工事|medieval fortress fort siege
sculpture|marble sculpture statue|雕塑塑造出立体形象|sculpture statue sculptor
ballot|ballot box voting|把选票投入票箱|ballot election nominate electoral
marathon|marathon runners race|长距离奔跑考验耐力|marathon endurance athletic stamina
recycle|recycling bins|分类收集，循环利用|recycle reuse recyclable
traffic|traffic junction aerial|道路交叉与车辆通行|intersection junction traffic bypass
erosion|soil erosion landscape|水流侵蚀地表|erosion erode degradation
irrigation|irrigation agricultural field|把水输送到农田|irrigation drainage irrigate
fire|fireplace burning logs|壁炉里燃烧的木柴|fireplace hearth combustion ignite kindle burner
compass|compass navigation|辨别方位，确定方向|orientation northward northwards southwards eastward compass
globe|earth globe|地球：用经纬度确定位置|latitude longitude equator hemisphere continental geographical
painting|artist painting easel|用画面描绘人物和场景|depict portray portrayal visual visualize
rocket|rocket launch|推进力让火箭升空|propulsion propel spacecraft launch
robot|industrial robot arm|机械臂重复执行操作|automate automation robot robotic
chain|metal chain links|链环彼此相连|interconnect link linkage attachment
butterfly|butterfly flower wings|翅膀轻轻扇动|flutter flap butterfly
sunset|sunset twilight landscape|太阳落下后的暮色|twilight dusk sunset
snow|snowflake macro|雪花的对称形状|flake symmetrical symmetry
crystal|quartz crystal|透明晶体的几何表面|quartz crystal crystalline
'''
words={x.split('\t')[0] for x in (A/'words.tsv').read_text(encoding='utf8').splitlines()}
groups=[];mapping={}
for line in DATA.strip().splitlines():
 sid,q,caption,ids=line.split('|'); valid=[w for w in ids.split() if w in words]
 if not valid:continue
 groups.append((sid,q,caption))
 for w in valid:mapping[w]={'scene':sid}

# These are invented near-sound hooks, deliberately NOT IPA, etymology, or examples.
MNEMONICS={
 'ambitious':'“俺必胜” → 有雄心、想赢。近音只帮记意思。',
 'ponder':'“胖的”人托腮想事情 → 沉思。',
 'pest':'“拍死它”里的“拍死” → 害虫。',
 'glove':'“哥拉我”时手戴着手套 → 手套。',
 'ambulance':'“俺不能死” → 急救车。',
 'jeopardize':'“这破袋子”把东西弄丢 → 危及。',
 'miser':'“买少”一点也舍不得花钱 → 吝啬鬼。',
 'shatter':'“摔它” → 摔得粉碎。',
 'stubborn':'“死大笨”不肯改主意 → 固执的。',
 'timely':'“太明理”地及时帮忙 → 及时的。',
 'caution':'“靠，小心！” → 谨慎；警告。',
 'famine':'“发米”救济 → 饥荒。',
 'tangle':'“藤勾”在一起 → 缠结。',
 'dizzy':'“低着”头站起来 → 头晕的。',
 'fuss':'“烦死”了，还大惊小怪 → 大惊小怪。',
 'sting':'“死叮”住你 → 刺痛。',
 'sneer':'“是你呀？”带着讥笑 → 嘲笑。',
 'jingle':'“叮铃”声一串串 → 叮当响。',
 'bungalow':'“搬个楼”，只搬一层 → 平房。',
 'bizarre':'“比萨”长得很奇怪 → 古怪的。',
 'cozy':'“靠着”软垫很惬意 → 舒适的。',
 'tug':'“拖个”重箱子 → 用力拉。',
 'snore':'“斯诺——”拖长的呼噜声 → 打鼾。',
 'hush':'“嘘”一声 → 使安静。',
 'weary':'“喂，累”了 → 疲倦的。',
 'dwell':'“待窝里” → 居住。',
 'clumsy':'“可笨死”了 → 笨拙的。',
 'frugal':'“腐乳够了”，吃得节省 → 节俭的。',
 'scoff':'“笑个”不停，满是讥讽 → 嘲弄。',
 'slumber':'“睡懒吧” → 睡眠。',
 'gloom':'“咕噜”着抱怨，心情阴沉 → 忧郁。',
}
for w,h in MNEMONICS.items():
 if w in words:mapping.setdefault(w,{})['mnemonic']=h

def plain(s):return html.unescape(re.sub('<[^>]+>','',s)).strip()
gate=threading.Lock();next_request=0.0
def get(url):
 global next_request
 for n in range(3):
  try:
   with gate:
    delay=next_request-time.monotonic()
    if delay>0:time.sleep(delay)
    next_request=time.monotonic()+3.1
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'WordHaloPersonal/0.10 (educational offline vocabulary; source attribution embedded)'}),timeout=25) as r:return r.read()
  except Exception as e:
   if n==2:raise
   if isinstance(e,urllib.error.HTTPError) and e.code==429:time.sleep(max(30,int(e.headers.get('Retry-After','30'))))
   else:time.sleep(2+n)
def scene(row):
 sid,q,caption=row;cached=W/(sid+'.json')
 try:
  pinned=json.loads((A/'scenes.json').read_text(encoding='utf8')) if (A/'scenes.json').exists() else {}
  if cached.exists():meta=json.loads(cached.read_text(encoding='utf8'))
  elif sid in pinned:meta=pinned[sid]
  else:
   query={'action':'query','generator':'search','gsrsearch':q+' filetype:bitmap','gsrnamespace':6,'gsrlimit':4,'prop':'imageinfo','iiprop':'url|extmetadata','iiurlwidth':640,'format':'json'}
   raw=json.loads(get('https://commons.wikimedia.org/w/api.php?'+urllib.parse.urlencode(query)))
   candidates=sorted(raw['query']['pages'].values(),key=lambda x:x.get('index',100))
   meta=None
   for page in candidates:
    info=page['imageinfo'][0]; ext=info['extmetadata'];license=plain(ext.get('LicenseShortName',{}).get('value',''))
    if not any(x in license for x in ['CC BY','CC0','Public domain']):continue
    if 'thumburl' not in info:continue
    meta={'title':page['title'],'url':info['thumburl'].split('?')[0],'source':info['descriptionurl'],'artist':plain(ext.get('Artist',{}).get('value','Unknown')),'license':license,'licenseUrl':ext.get('LicenseUrl',{}).get('value','https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia'),'caption':caption,'file':'photo-'+sid+'.webp'};break
   if meta is None:raise ValueError('No reusable photograph')
   cached.write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf8')
  meta['caption']=caption
  out=A/meta['file']
  if not out.exists():
   img=Image.open(io.BytesIO(get(meta['url']))).convert('RGB');img.thumbnail((420,300));img.save(out,'WEBP',quality=84,method=6)
  return sid,meta
 except Exception as e:print('FAILED',sid,str(e),flush=True);return sid,None

if __name__=='__main__':
 scenes={}
 with concurrent.futures.ThreadPoolExecutor(3) as pool:
  for sid,meta in pool.map(scene,groups):
   if meta:scenes[sid]=meta;print('OK',sid,flush=True)
 for w,m in mapping.items():
  if m.get('scene') not in scenes:m.pop('scene',None)
 mapping={w:m for w,m in mapping.items() if m}
 # Generic IPA comes from the existing MIT ECDICT source. Regional IPA is fetched
 # on demand and never inferred by swapping letters in this generic pronunciation.
 with open('work/watch-build/ecdict.csv',encoding='utf-8-sig',newline='') as f:
  for row in csv.DictReader(f):
   if row['word'] in words and row['phonetic']:mapping.setdefault(row['word'],{})['ipa']=row['phonetic']
 (A/'word-media.json').write_text(json.dumps(mapping,ensure_ascii=False,separators=(',',':')),encoding='utf8')
 (A/'scenes.json').write_text(json.dumps(scenes,ensure_ascii=False,indent=2),encoding='utf8')
 items=list(scenes.items())
 for part in range((len(items)+29)//30):
  sheet=Image.new('RGB',(1000,900),'#151a18');d=ImageDraw.Draw(sheet)
  for i,(sid,m) in enumerate(items[part*30:(part+1)*30]):
   im=Image.open(A/m['file']);im.thumbnail((190,120));x=(i%5)*200;y=(i//5)*150;sheet.paste(im,(x+(200-im.width)//2,y));d.text((x+5,y+123),sid,fill='white')
  sheet.save(W/f'contact-{part+1}.jpg')
 print(json.dumps({'photos':len(scenes),'picturedWords':sum('scene'in m for m in mapping.values()),'mnemonics':sum('mnemonic'in m for m in mapping.values())}))
