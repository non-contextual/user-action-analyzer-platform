#!/usr/bin/env python3
"""
电商用户行为分析大数据平台 - 模拟数据生成脚本
生成文件:
  - /opt/data/user_info.csv          用户基本信息
  - /opt/data/user_visit_action.csv  用户行为日志
"""

import csv
import os
import random
import uuid
from datetime import datetime, timedelta
from faker import Faker

fake = Faker('zh_CN')
random.seed(42)

# ============================================================
# 配置参数
# ============================================================
OUTPUT_DIR = "/opt/data"
USER_COUNT = 1000          # 用户数
SESSION_COUNT = 5000       # Session 总数
MAX_ACTIONS_PER_SESSION = 20  # 每个 Session 最大行为数

# 业务数据
PROFESSIONS = ["学生", "白领", "工人", "自由职业", "教师", "医生", "商人", "程序员", "设计师", "运营"]
CITIES = ["北京", "上海", "深圳", "广州", "杭州", "成都", "武汉", "南京", "重庆", "西安"]
CITY_IDS = {city: idx + 1 for idx, city in enumerate(CITIES)}
KEYWORDS = ["女装", "男装", "童装", "手机", "电脑", "耳机", "书包", "零食", "护肤品", "家电",
            "运动鞋", "玩具", "图书", "音箱", "相机", "平板", "智能手表", "生鲜", "家具", "茶叶"]
CATEGORIES = list(range(1, 51))   # 50 个品类
PRODUCTS = list(range(1, 201))    # 200 个商品
PAGES = list(range(1, 21))        # 20 个页面
START_DATE = datetime(2019, 1, 1)
END_DATE = datetime(2019, 12, 31)


def random_date(start: datetime, end: datetime) -> datetime:
    delta = end - start
    return start + timedelta(seconds=random.randint(0, int(delta.total_seconds())))


def ids_to_str(id_list):
    return ",".join(map(str, id_list)) if id_list else ""


# ============================================================
# 生成 user_info.csv
# ============================================================
def generate_user_info():
    filepath = os.path.join(OUTPUT_DIR, "user_info.csv")
    users = []
    with open(filepath, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["user_id", "username", "name", "age", "professional", "city", "sex"])
        for user_id in range(1, USER_COUNT + 1):
            age = random.randint(10, 65)
            professional = random.choice(PROFESSIONS)
            city = random.choice(CITIES)
            sex = random.choice(["male", "female"])
            username = fake.user_name()
            name = fake.name()
            writer.writerow([user_id, username, name, age, professional, city, sex])
            users.append({"user_id": user_id, "age": age, "professional": professional,
                          "city": city, "sex": sex})
    print(f"[OK] Generated {USER_COUNT} users -> {filepath}")
    return users


# ============================================================
# 生成 user_visit_action.csv
# ============================================================
def generate_user_visit_action(users):
    filepath = os.path.join(OUTPUT_DIR, "user_visit_action.csv")
    total_actions = 0

    with open(filepath, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "date", "user_id", "session_id", "page_id",
            "action_time", "search_keyword",
            "click_category_id", "click_product_id",
            "order_category_ids", "order_product_ids",
            "pay_category_ids", "pay_product_ids",
            "city_id"
        ])

        for _ in range(SESSION_COUNT):
            user = random.choice(users)
            session_id = str(uuid.uuid4()).replace("-", "")
            city = user["city"]
            city_id = CITY_IDS[city]

            session_start = random_date(START_DATE, END_DATE)
            action_count = random.randint(1, MAX_ACTIONS_PER_SESSION)

            current_time = session_start
            for step in range(action_count):
                current_time += timedelta(seconds=random.randint(1, 60))
                date_str = current_time.strftime("%Y-%m-%d")
                action_time_str = current_time.strftime("%Y-%m-%d %H:%M:%S")
                page_id = random.choice(PAGES)

                # 行为类型：搜索 / 点击 / 下单 / 支付
                action_type = random.choices(
                    ["search", "click", "order", "pay"],
                    weights=[30, 50, 15, 5]
                )[0]

                search_keyword = ""
                click_category_id = -1
                click_product_id = -1
                order_category_ids = ""
                order_product_ids = ""
                pay_category_ids = ""
                pay_product_ids = ""

                if action_type == "search":
                    search_keyword = random.choice(KEYWORDS)
                elif action_type == "click":
                    click_category_id = random.choice(CATEGORIES)
                    click_product_id = random.choice(PRODUCTS)
                elif action_type == "order":
                    n = random.randint(1, 3)
                    order_category_ids = ids_to_str(random.sample(CATEGORIES, n))
                    order_product_ids = ids_to_str(random.sample(PRODUCTS, n))
                elif action_type == "pay":
                    n = random.randint(1, 2)
                    pay_category_ids = ids_to_str(random.sample(CATEGORIES, n))
                    pay_product_ids = ids_to_str(random.sample(PRODUCTS, n))

                writer.writerow([
                    date_str, user["user_id"], session_id, page_id,
                    action_time_str, search_keyword,
                    click_category_id, click_product_id,
                    order_category_ids, order_product_ids,
                    pay_category_ids, pay_product_ids,
                    city_id
                ])
                total_actions += 1

    print(f"[OK] Generated {SESSION_COUNT} sessions / {total_actions} actions -> {filepath}")


# ============================================================
# 主函数
# ============================================================
if __name__ == "__main__":
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print("=== 开始生成模拟数据 ===")
    users = generate_user_info()
    generate_user_visit_action(users)
    print("=== 数据生成完成 ===")
    print(f"文件位置: {OUTPUT_DIR}/")
    print(f"  - user_info.csv         ({USER_COUNT} 条)")
    print(f"  - user_visit_action.csv ({SESSION_COUNT} 个 Session)")
