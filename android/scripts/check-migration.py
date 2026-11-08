"""Host-side SQL contract for 3→4; supplements, never replaces, Room device tests."""
import json
from pathlib import Path
import re
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/de.lmaa.app.history.LmaaDatabase"
SOURCE = ROOT / "app/src/main/java/de/lmaa/app/history/LmaaDatabase.kt"


def schema(db, version):
    document = json.loads((SCHEMAS / f"{version}.json").read_text())["database"]
    for table in document["entities"]:
        name = table["tableName"]
        db.execute(table["createSql"].replace("${TABLE_NAME}", name))
        for index in table.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", name))


def structure(db):
    result = {}
    for (name,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name != 'sqlite_sequence'"):
        result[name] = {
            "columns": sorted(tuple(row[1:]) for row in db.execute(f"PRAGMA table_info('{name}')")),
            "foreign_keys": sorted(tuple(row[2:]) for row in db.execute(f"PRAGMA foreign_key_list('{name}')")),
            "indices": sorted((row[1], row[2], tuple((column[0], column[2]) for column in db.execute(f"PRAGMA index_info('{row[1]}')")))
                              for row in db.execute(f"PRAGMA index_list('{name}')") if row[3] != 'pk'),
        }
    return result


def main():
    source = SOURCE.read_text().split("internal val MIGRATION_3_4 =", 1)[1].split("internal val MIGRATION_1_2", 1)[0]
    statements = re.findall(r'db\.execSQL\("""([\s\S]*?)"""\)|db\.execSQL\("([^"\n]*)"\)', source)
    assert len(statements) == 5
    expected = sqlite3.connect(":memory:")
    schema(expected, 4)
    work = ROOT / "work"
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work) as directory:
        path = Path(directory) / "migration.db"
        db = sqlite3.connect(path)
        db.execute("PRAGMA foreign_keys=ON")
        schema(db, 3)
        db.execute("INSERT INTO videos VALUES ('abcdefghijk','https://www.youtube.com/watch?v=abcdefghijk','Titel',NULL,'Kanal',NULL,NULL,NULL,'',1)")
        db.execute("INSERT INTO transcripts VALUES (1,'abcdefghijk','primary','de',0,'[]','Original',1)")
        db.execute("INSERT INTO briefings VALUES (1,'abcdefghijk',1,'Eigener Stil','Ein Satz','Deutsch','gpt-5.6-sol','Originalbriefing',1,'COMPLETED',NULL,1)")
        before = db.execute("SELECT * FROM briefings").fetchone()
        for multi, single in statements:
            db.execute(multi or single)
        assert structure(db) == structure(expected), "Migration differs from Room-generated schema 4"
        assert db.execute("SELECT * FROM briefings").fetchone()[:-1] == before
        portable_id = db.execute("SELECT portableId FROM briefings").fetchone()[0]
        assert re.fullmatch("[a-f0-9]{32}", portable_id)
        db.execute("INSERT INTO chat_messages VALUES ('message-1',1,'user','Frage',2,0,NULL)")
        db.commit()
        db.close()
        db = sqlite3.connect(path)
        db.execute("PRAGMA foreign_keys=ON")
        assert db.execute("SELECT portableId FROM briefings").fetchone()[0] == portable_id
        db.execute("DELETE FROM briefings WHERE id=1")
        assert db.execute("SELECT COUNT(*) FROM chat_messages").fetchone()[0] == 0
        assert not db.execute("PRAGMA foreign_key_check").fetchall()
        db.close()
    print("PASS: actual migration SQL matches Room schema 4; history, stable IDs, reopen and chat cascade verified.")


if __name__ == "__main__":
    main()
