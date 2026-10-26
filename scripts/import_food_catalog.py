"""Build an offline reference catalogue from public product pages, never account APIs.
McDonald's: China official nutrition (kJ -> kcal), product photos.
KFC: 5iKFC historical reference menu; NEVER label it a current official menu.
Missing nutrition stays null and must be supplied before saving a meal.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urljoin, quote, urlsplit, urlunsplit
from lxml import html
from PIL import Image
import io, json, re, hashlib

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/main/assets/foods'
CACHE = ROOT / '.local/food-research'
OUT.mkdir(parents=True, exist_ok=True)
CACHE.mkdir(parents=True, exist_ok=True)

def fetch(url):
    parts = urlsplit(url)
    url = urlunsplit((parts.scheme, parts.netloc, quote(parts.path, safe='/%'), parts.query, ''))
    return urlopen(Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.mcdonalds.com.cn/'}), timeout=25).read()

def page(url):
    file = CACHE / (hashlib.sha256(url.encode()).hexdigest()[:18]+'.html')
    if not file.exists(): file.write_bytes(fetch(url))
    return html.fromstring(file.read_bytes().decode('utf-8', errors='replace'))

def photo(url, key):
    name = key+'.webp'
    try:
        if not (OUT/name).exists():
            image = Image.open(io.BytesIO(fetch(url))).convert('RGBA')
            image.thumbnail((256, 256))
            image.save(OUT/name, 'WEBP', quality=82)
        return 'file:///android_asset/foods/'+name
    except Exception:
        return ''

def mcd_category(category):
    url='https://www.mcdonalds.com.cn/product/mcdonalds/'+category
    doc=page(url)
    result=[]
    for a in doc.xpath('//a[.//span[@class="name"] and .//img]'):
        href=urljoin(url,a.get('href',''))
        name=''.join(a.xpath('.//span[@class="name"]/text()')).strip()
        img=a.xpath('.//img/@src')[0]
        if name and '/product/' in href: result.append((href,name,img,category))
    return result

def mcd_item(entry):
    url,name,img,category=entry
    key='mcd-'+hashlib.sha256(url.encode()).hexdigest()[:12]
    item=dict(id=key,name=name,brand='麦当劳',category={'hamburgers':'汉堡和卷','beverage':'饮品','Snacks':'小食','desserts':'甜品','Breakfast-26':'早餐','500大卡套餐-2':'套餐','Happy-Meal-Items-2':'儿童餐'}[category],
              portion='1份',grams=None,calories=None,protein=None,carbs=None,fat=None,
              image=photo(img,key),sourceUrl=url,imageSource=img,source='麦当劳中国官网 · 单份营养',estimated=False)
    try:
        doc=page(url)
        portions=doc.xpath('//div[contains(@class,"table-nav")]//li/text()')
        tables=doc.xpath('//div[contains(@class,"classified-table")]//table')
        if tables:
            values=[]
            for td in tables[0].xpath('.//tbody//td'):
                raw=''.join(td.xpath('.//strong/text()')).strip()
                values.append(float(raw.replace(',','')) if re.fullmatch(r'[\d.,]+',raw) else None)
            if len(values)>=4:
                item.update(calories=round(values[0]/4.184,1) if values[0] is not None else None,protein=values[1],fat=values[2],carbs=values[3])
        if portions: item['portion']=portions[0].strip()
    except Exception: pass
    variants=[]
    if 'tables' in locals() and len(tables)>1:
        for index,table in enumerate(tables):
            values=[]
            for td in table.xpath('.//tbody//td'):
                raw=''.join(td.xpath('.//strong/text()')).strip()
                values.append(float(raw.replace(',','')) if re.fullmatch(r'[\d.,]+',raw) else None)
            if len(values)<4: continue
            variant=dict(item)
            portion=portions[index].strip() if index<len(portions) else '规格'+str(index+1)
            unit='杯' if category=='beverage' or '豆浆' in name else '份'
            variant.update(id=key+'-'+str(index),name=name+'（'+portion+'）',portion='1'+unit+'（'+portion+'）',
                calories=round(values[0]/4.184,1) if values[0] is not None else None,protein=values[1],fat=values[2],carbs=values[3])
            variants.append(variant)
    return variants or [item]

def kfc_category(category):
    url='http://www.5ikfc.com/kfc/menu/'+category
    doc=page(url); result=[]
    for div in doc.xpath('//div[@class="li"]'):
        links=div.xpath('.//a[@href]');images=div.xpath('.//img/@src')
        if len(links)>=2 and images:
            name=links[1].text_content().strip()
            if name and not any(x in name for x in ['套餐','桶','餐盒','K记','龙虾','饭桶','全家','儿童餐']):
                result.append((links[0].get('href'),name,images[0],category))
    return result

def kfc_item(entry):
    url,name,img,category=entry
    key='kfc-'+hashlib.sha256(url.encode()).hexdigest()[:12]
    item=dict(id=key,name=name,brand='肯德基',category={'Meal':'主食','Snack':'小食','DessertAndDrink':'甜品饮品','Breakfast':'早餐','SideItem':'配餐','coffee':'咖啡'}[category],
              portion='1份',grams=None,calories=None,protein=None,carbs=None,fat=None,image=photo(img,key),
              sourceUrl=url,imageSource=img,source='5iKFC 历史参考菜单 · 非官方现售清单',estimated=True)
    try:
        doc=page(url)
        text=doc.text_content()
        for table in doc.xpath('//table'):
            t=table.text_content()
            if '热量' not in t and '能量' not in t: continue
            for tr in table.xpath('.//tr'):
                cells=[x.text_content().strip() for x in tr.xpath('./td|./th')]
                for i,c in enumerate(cells):
                    if i+1<len(cells):
                        v=re.search(r'(\d+(?:\.\d+)?)',cells[i+1])
                        if v:
                            for label,keyname in [('热量','calories'),('蛋白质','protein'),('碳水化合物','carbs'),('脂肪','fat')]:
                                if label in c: item[keyname]=float(v.group(1))
        # Do not guess from prices, grams or an ambiguous table.
    except Exception: pass
    return item

if __name__=='__main__':
    with ThreadPoolExecutor(max_workers=6) as pool:
        mcd_entries=sum(list(pool.map(mcd_category,['hamburgers','beverage','Snacks','desserts','Breakfast-26','500大卡套餐-2','Happy-Meal-Items-2'])),[])
        mcd_entries=list({e[0]:e for e in mcd_entries}.values())
        mcd=sum(list(pool.map(mcd_item,mcd_entries)),[])
        print('McDonalds',len(mcd),flush=True)
        kfc_entries=sum(list(pool.map(kfc_category,['Meal','Snack','DessertAndDrink','Breakfast','SideItem','coffee'])),[])
        kfc_entries=list({e[0]:e for e in kfc_entries}.values())
        kfc=list(pool.map(kfc_item,kfc_entries))
    supplements=OUT/'nutrition-estimates.json'
    reference=json.loads(supplements.read_text(encoding='utf-8')) if supplements.exists() else {}
    for item in kfc:
        if item['name'] in reference:
            values=reference[item['name']]
            item.update(calories=values['calories'],portion=values['portion'],source=values['source'],
                menuSourceUrl=item['sourceUrl'],sourceUrl=values['sourceUrl'],estimated=True)
    kfc.sort(key=lambda x:x['calories'] is None)
    (OUT/'catalog.json').write_text(json.dumps(mcd+kfc,ensure_ascii=False,indent=2),encoding='utf-8')
    print('KFC',len(kfc),'with nutrition',sum(x['calories'] is not None for x in mcd+kfc),'photos',sum(bool(x['image']) for x in mcd+kfc),flush=True)
