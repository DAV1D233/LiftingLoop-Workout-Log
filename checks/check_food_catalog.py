"""Validate offline data/asset integrity and representative official portion conversions."""
from pathlib import Path
import json, math
from PIL import Image

root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
foods=sum([json.loads((assets/'foods'/name).read_text(encoding='utf-8')) for name in ['catalog.json','home.json']],[])
assert len({f['id'] for f in foods})==len(foods)
for food in foods:
    assert food['name'] and food['portion'] and food['source']
    for nutrient in ['calories','protein','carbs','fat','grams']:
        value=food.get(nutrient)
        assert value is None or math.isfinite(value) and value>=0, (food['name'],nutrient)
    assert food['image'].startswith('file:///android_asset/')
    image=assets/food['image'].removeprefix('file:///android_asset/')
    assert image.is_file(), image
    with Image.open(image) as im:
        assert im.width<=320 and im.height<=320
        im.verify()
big=next(f for f in foods if f['name']=='巨无霸')
assert abs(big['calories']-2146/4.184)<.1
chicken=next(f for f in foods if f['id']=='home-chicken-breast')
assert chicken['grams']==150 and chicken['estimated'] and chicken['calories']==247.5
assert any(f['brand']=='麦当劳' and f['portion']=='1杯（小）' for f in foods)
assert any(f['brand']=='肯德基' and f['calories'] is None for f in foods)
print(f'Food catalogue: {len(foods)} distinct entries, all offline thumbnails decoded, units and unknowns checked')
