"""生成 200 家店铺 + 描述语料 + 100 篇笔记的种子 SQL。
用法: python generate_seed.py > seed.sql
"""
import random

random.seed(20261030)

AREAS = {
    "西湖区": ["黄龙", "文三路", "转塘", "西溪"],
    "上城区": ["湖滨", "钱江新城", "四季青", "吴山"],
    "拱墅区": ["武林", "大关", "拱宸桥", "运河上街"],
    "滨江区": ["滨江宝龙", "江南大道", "浦沿"],
    "余杭区": ["未来科技城", "良渚", "仓前"],
    "萧山区": ["钱江世纪城", "北干", "闻堰"],
    "钱塘区": ["下沙", "金沙湖"],
}

TYPES = {
    1: {
        "name": "美食",
        "bases": ["老码头火锅", "江南小馆", "金牌烤鱼", "粤味轩", "西溪别院", "蟹粉小笼",
                  "撸串研究所", "云上寿司", "半山咖啡", "巷子里私房菜"],
        "features": ["重庆牛油锅底，毛肚和鲜鸭血是招牌", "杭帮菜做法，东坡肉和龙井虾仁稳定发挥",
                     "炭火烤鱼现杀现做，配菜可以免费续", "广式烧腊每天两轮出炉，例汤限量",
                     "手冲咖啡豆每周轮换，甜点现做", "日料板前位需要提前预约"],
        "tags": ["适合约会，灯光柔和有卡座", "适合朋友聚餐，人多热闹", "安静不吵，适合谈事",
                 "深夜营业，适合宵夜", "拍照好看，装修出片"],
        "price": (45, 320),
        "hours": ["10:00-22:00", "11:00-23:00", "17:00-02:00", "09:00-21:00"],
    },
    2: {
        "name": "KTV",
        "bases": ["麦乐迪KTV", "星聚会KTV", "温莎KTV", "纯K", "钱柜KTV", "唱吧麦颂"],
        "features": ["包厢带独立卫生间和小吧台", "曲库更新快，老歌和粤语歌都全",
                     "音响是雅马哈全套，隔音做得好", "有迷你包厢，两个人也能唱"],
        "tags": ["适合生日聚会，可布置气球", "适合公司团建，能容纳 20 人", "下午场便宜，学生多",
                 "酒水可以自带，收开瓶费"],
        "price": (60, 200),
        "hours": ["12:00-02:00", "14:00-03:00", "10:00-24:00"],
    },
    3: {
        "name": "丽人·美发",
        "bases": ["东田造型", "木北造型", "丝域养发", "尚品发型", "发艺工坊"],
        "features": ["总监剪发需要提前三天约", "主打日系空气烫和法式短发",
                     "头皮护理用仪器检测，会出报告", "染发用植物染，味道不刺鼻"],
        "tags": ["适合上班族，午休可以做完", "有独立包间，安静", "学生凭证件打折", "拍照出片"],
        "price": (80, 680),
        "hours": ["10:00-21:00", "09:30-20:30"],
    },
    10: {
        "name": "美睫·美甲",
        "bases": ["小鹿美甲", "指尖物语", "睫毛研究所", "Bling美甲", "素颜美睫"],
        "features": ["甲油胶用的是日本进口胶，能保持四周", "嫁接睫毛主打自然款，不压眼",
                     "有单间，做的时候能睡着", "可以做手绘和猫眼"],
        "tags": ["适合约会前急救", "款式更新快，小红书同款", "消毒流程规范", "价格透明不推销"],
        "price": (68, 398),
        "hours": ["10:00-21:00", "11:00-22:00"],
    },
    5: {
        "name": "按摩·足疗",
        "bases": ["足下登", "富侨足道", "泰合玺", "禅意足疗", "筋骨堂"],
        "features": ["泰式古法按摩，力度可以调", "足疗带药包泡脚，按完有糖水",
                     "师傅都是十年以上经验", "有双人间，适合夫妻一起来"],
        "tags": ["适合久坐上班族放松", "安静，适合睡觉", "可以过夜，加钱有躺椅", "环境干净"],
        "price": (98, 368),
        "hours": ["12:00-02:00", "10:00-24:00"],
    },
    6: {
        "name": "美容SPA",
        "bases": ["克丽缇娜", "思妍丽", "美丽田园", "自然美SPA", "悦己芳疗"],
        "features": ["主打深层清洁和补水，做完不紧绷", "芳疗用单方精油，可以自己选味道",
                     "仪器是进口的，做之前会测肤", "有肩颈放松，适合久坐人群"],
        "tags": ["适合婚前护理", "环境安静，有独立淋浴", "不推销办卡", "手法专业"],
        "price": (198, 880),
        "hours": ["10:00-21:00", "09:00-20:00"],
    },
    7: {
        "name": "亲子游乐",
        "bases": ["奇乐儿童乐园", "麦鲁小城", "宝贝王", "萌宠乐园", "乐高探索中心"],
        "features": ["室内恒温，有海洋球和滑梯", "有职业体验区，能玩一整天",
                     "有母婴室和儿童餐", "周末有手工课"],
        "tags": ["适合 3-8 岁小朋友", "家长可以陪同入场", "办卡划算", "停车方便"],
        "price": (68, 268),
        "hours": ["09:30-20:00", "10:00-21:00"],
    },
    8: {
        "name": "酒吧",
        "bases": ["贰麻酒馆", "小酒馆", "Micro酒吧", "爵士酒廊", "江湖酒吧"],
        "features": ["精酿有十二个酒头，每周换一款", "有现场驻唱，民谣为主",
                     "调酒师拿过华东区比赛名次", "露台能看到江景"],
        "tags": ["适合约会，灯光暗", "适合朋友小聚，不吵", "周末有演出，需要预约", "下酒小食好吃"],
        "price": (88, 300),
        "hours": ["18:00-02:00", "19:00-03:00"],
    },
    9: {
        "name": "轰趴馆",
        "bases": ["一起轰趴", "别墅派对", "时光机轰趴馆", "嗨玩空间"],
        "features": ["独栋别墅，带院子可以烧烤", "有台球、KTV、游戏机，通宵不限时",
                     "可以做饭，厨房设备齐全", "有投影，能开会也能看电影"],
        "tags": ["适合生日和团建", "能容纳 30 人", "可以过夜", "周边停车方便"],
        "price": (120, 480),
        "hours": ["10:00-次日02:00", "14:00-次日10:00"],
    },
    4: {
        "name": "健身运动",
        "bases": ["乐刻健身", "超级猩猩", "拳击俱乐部", "瑜伽生活馆", "攀岩馆"],
        "features": ["24 小时营业，刷脸进场", "团课以小班为主，最多 12 人",
                     "有拳击台和沙袋区", "瑜伽主打阴瑜伽和空中瑜伽"],
        "tags": ["适合上班族下班后练", "有淋浴和吹风机", "教练不推销私教", "新手有体验课"],
        "price": (58, 399),
        "hours": ["06:00-23:00", "24 小时营业", "09:00-22:00"],
    },
}

BLOG_TITLES = [
    "在{name}待了一下午，安静得不想走",
    "周末探店｜{name}值不值得来",
    "{area}这家{type}，人均{price}吃到扶墙",
    "被朋友安利的{name}，确实没踩雷",
    "在{name}过生日，氛围感拉满",
    "{type}测评：{name}和隔壁比赢在哪",
]

BLOG_BODY = [
    "提前一天订的位置，到店不用等。{feature}，整体{taste}。店里{env}，服务员不会一直过来打扰，适合慢慢坐。",
    "冲着评价来的，工作日晚上人不多。{feature}，分量比想象中足。{env}，结账人均{price}，性价比可以。",
    "位置在{area}{sub}，跟着导航好找，门口能停车。{feature}。{env}，下次还会再来。",
]

TASTE = ["口味偏清淡", "调味比较重口", "出品稳定", "比预期好吃", "中规中矩但不出错"]
ENVS = ["灯光偏暗", "装修偏工业风", "座位间距大", "有点吵但很热闹", "干净整洁", "有独立的包间"]
PER_TYPE = 20


def esc(text) -> str:
    return str(text).replace("\\", "\\\\").replace("'", "''")


def build_shops():
    shops = []
    shop_id = 15
    for type_id, conf in TYPES.items():
        combos = []
        for base in conf["bases"]:
            for area, subs in AREAS.items():
                for sub in subs:
                    combos.append((base, area, sub))
        random.shuffle(combos)
        for base, area, sub in combos[:PER_TYPE]:
            price = random.randint(*conf["price"])
            feature = random.choice(conf["features"])
            tag = random.choice(conf["tags"])
            shops.append({
                "id": shop_id,
                "type_id": type_id,
                "type_name": conf["name"],
                "name": f"{base}（{sub}店）",
                "area": area,
                "sub": sub,
                "address": f"{area}{sub}路{random.randint(1, 999)}号",
                "x": round(random.uniform(120.10, 120.28), 6),
                "y": round(random.uniform(30.20, 30.36), 6),
                "price": price,
                "sold": random.randint(120, 18000),
                "comments": random.randint(30, 4200),
                "score": random.randint(40, 50),
                "hours": random.choice(conf["hours"]),
                "feature": feature,
                "desc": f"{feature}。位于{area}{sub}，{tag}。人均{price}元，营业时间{random.choice(conf['hours'])}。",
            })
            shop_id += 1
    return shops


def main() -> None:
    shops = build_shops()
    out = ["SET NAMES utf8mb4;", "USE zhimi_life;", ""]

    out.append("CREATE TABLE IF NOT EXISTS tb_shop_doc ("
               "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
               "shop_id BIGINT UNSIGNED NOT NULL,"
               "content VARCHAR(512) NOT NULL,"
               "PRIMARY KEY (id), UNIQUE KEY uk_shop (shop_id)"
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")
    out.append("CREATE TABLE IF NOT EXISTS tb_shop_vector ("
               "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
               "shop_id BIGINT UNSIGNED NOT NULL,"
               "model VARCHAR(64) NOT NULL,"
               "dims INT NOT NULL,"
               "embedding MEDIUMTEXT NOT NULL,"
               "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
               "PRIMARY KEY (id), UNIQUE KEY uk_shop (shop_id)"
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")
    out.append("")
    out.append("DELETE FROM tb_blog WHERE id >= 28;")
    out.append("DELETE FROM tb_shop_doc;")
    out.append("DELETE FROM tb_shop_vector;")
    out.append("DELETE FROM tb_shop WHERE id >= 15;")
    out.append("")

    rows = []
    for s in shops:
        rows.append(
            f"({s['id']},'{esc(s['name'])}',{s['type_id']},'/imgs/shops/default.jpg','{esc(s['area'])}',"
            f"'{esc(s['address'])}',{s['x']},{s['y']},{s['price']},{s['sold']},{s['comments']},"
            f"{s['score']},'{esc(s['hours'])}',NOW(),NOW())"
        )
    out.append("INSERT INTO tb_shop (id,name,type_id,images,area,address,x,y,avg_price,sold,comments,score,open_hours,create_time,update_time) VALUES")
    out.append(",\n".join(rows) + ";")
    out.append("")

    docs = [f"({s['id']},'{esc(s['desc'])}')" for s in shops]
    out.append("INSERT INTO tb_shop_doc (shop_id,content) VALUES")
    out.append(",\n".join(docs) + ";")
    out.append("")

    blogs = []
    for i in range(100):
        s = random.choice(shops)
        title = random.choice(BLOG_TITLES).format(
            name=s["name"], area=s["area"], type=s["type_name"], price=s["price"])
        body = random.choice(BLOG_BODY).format(
            feature=s["feature"], taste=random.choice(TASTE), env=random.choice(ENVS),
            area=s["area"], sub=s["sub"], price=s["price"])
        blogs.append(
            f"({28 + i},{s['id']},{random.randint(1, 900)},'{esc(title)}','/imgs/blogs/default.jpg',"
            f"'{esc(body)}',{random.randint(0, 800)},{random.randint(0, 60)},NOW(),NOW())"
        )
    out.append("INSERT INTO tb_blog (id,shop_id,user_id,title,images,content,liked,comments,create_time,update_time) VALUES")
    out.append(",\n".join(blogs) + ";")

    print("\n".join(out))


if __name__ == "__main__":
    main()
