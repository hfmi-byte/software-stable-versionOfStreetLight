import pymysql

conn = pymysql.connect(
    host="47.108.58.107", port=3306,
    user="root", password="c0765083cd3f57ab",
    database="dream22", charset="utf8mb4"
)

# 临时把 light001 标成在线，用于测试下行命令（绕过离线保护）
with conn.cursor() as cur:
    cur.execute("UPDATE device_status SET online=1 WHERE device_id='light001'")
    print("已把 light001 标为在线，影响行数", cur.rowcount)
    conn.commit()

conn.close()
