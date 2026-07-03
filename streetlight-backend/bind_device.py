import pymysql

conn = pymysql.connect(
    host="47.108.58.107", port=3306,
    user="root", password="c0765083cd3f57ab",
    database="dream22", charset="utf8mb4"
)

# 把左边的 light00x 绑到右边华为云注册的设备ID
bindings = {
    "light001": "6a436d987f2e6c302f8080a3_20260630",
}

with conn.cursor() as cur:
    for local_id, huawei_id in bindings.items():
        cur.execute(
            "UPDATE devices SET huawei_device_id=%s WHERE id=%s",
            (huawei_id, local_id)
        )
        print(f"{local_id} -> {huawei_id}, 影响行数 {cur.rowcount}")
    conn.commit()

# 确认结果
with conn.cursor() as cur:
    cur.execute("SELECT id, name, huawei_device_id FROM devices")
    for row in cur.fetchall():
        print(row)

conn.close()
