param([string]$ToolchainRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '../toolchain'))
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $ToolchainRoot 'jdk-17.0.20.1+1/bin'
$classes = Join-Path $workspace 'app/build/tmp/kotlin-classes/debug'
$stdlib = (Get-ChildItem (Join-Path $ToolchainRoot 'gradle-home/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.20') -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$output = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp "$classes;$stdlib" -d $output (Join-Path $PSScriptRoot 'WorkoutEngineCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Build the debug app before running domain checks.' }
& (Join-Path $jdk 'java.exe') -cp "$output;$classes;$stdlib" WorkoutEngineCheck
if ($LASTEXITCODE -ne 0) { throw 'Domain regression failed.' }

& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp "$classes;$stdlib" -d $output (Join-Path $PSScriptRoot 'CardioEngineCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Cardio checks failed to compile.' }
& (Join-Path $jdk 'java.exe') -cp "$output;$classes;$stdlib" CardioEngineCheck
if ($LASTEXITCODE -ne 0) { throw 'Cardio state regression failed.' }

& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp "$classes;$stdlib" -d $output (Join-Path $PSScriptRoot 'FoodTagsCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Food tag checks failed to compile.' }
& (Join-Path $jdk 'java.exe') -cp "$output;$classes;$stdlib" FoodTagsCheck
if ($LASTEXITCODE -ne 0) { throw 'Food tag regression failed.' }

& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp "$classes;$stdlib" -d $output (Join-Path $PSScriptRoot 'ScheduleCatchUpCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Schedule catch-up checks failed to compile.' }
& (Join-Path $jdk 'java.exe') -cp "$output;$classes;$stdlib" ScheduleCatchUpCheck
if ($LASTEXITCODE -ne 0) { throw 'Schedule catch-up regression failed.' }

& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp "$classes;$stdlib" -d $output (Join-Path $PSScriptRoot 'BiliVideoCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Video link checks failed to compile.' }
& (Join-Path $jdk 'java.exe') -cp "$output;$classes;$stdlib" BiliVideoCheck
if ($LASTEXITCODE -ne 0) { throw 'Video link regression failed.' }

& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp "$classes;$stdlib" -d $output (Join-Path $PSScriptRoot 'WorkoutShareCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Workout share checks failed to compile.' }
& (Join-Path $jdk 'java.exe') -cp "$output;$classes;$stdlib" WorkoutShareCheck
if ($LASTEXITCODE -ne 0) { throw 'Workout share pagination/Gaussian regression failed.' }

$sqlite = Join-Path $ToolchainRoot 'android-sdk/platform-tools/sqlite3.exe'
$dbSource = [IO.File]::ReadAllText((Join-Path $workspace 'app/src/main/java/com/fitlog/app/data/AppDatabase.kt'))
$migrations = [regex]::Matches($dbSource, 'db.execSQL\("([^"]+)"\)') | ForEach-Object { $_.Groups[1].Value + ';' }
if ($migrations.Count -ne 16) { throw 'Update the migration regression for the new schema.' }
$generated = [IO.File]::ReadAllText((Join-Path $workspace 'app/build/generated/ksp/debug/java/com/fitlog/app/data/AppDatabase_Impl.java'))
$createSection = $generated.Substring($generated.IndexOf('public void createAllTables'), $generated.IndexOf('public void dropAllTables') - $generated.IndexOf('public void createAllTables'))
$creates = [regex]::Matches($createSection, 'db.execSQL\("([^"]+)"\)') | ForEach-Object { $_.Groups[1].Value + ';' }
$legacy = $creates | Where-Object { -not $_.Contains('index_workout_records_sessionKey') } | ForEach-Object {
    $_.Replace(', `proteinGrams` REAL, `carbsGrams` REAL, `fatGrams` REAL', '').Replace(', `sessionKey` TEXT', '').Replace(', `volumeKg` REAL', '').Replace(', `isBodyweight` INTEGER NOT NULL DEFAULT 0', '').Replace(', `muscleGroup` TEXT NOT NULL DEFAULT ''''', '').Replace(', `iconPath` TEXT NOT NULL DEFAULT ''''', '').Replace(', `videoUrl` TEXT NOT NULL DEFAULT ''''', '').Replace(', `isCardio` INTEGER NOT NULL DEFAULT 0', '').Replace(', `setTargetsJson` TEXT NOT NULL DEFAULT ''[]''', '').Replace(', `cardioJson` TEXT NOT NULL DEFAULT ''{}''', '').Replace(', `cardioSeconds` INTEGER NOT NULL DEFAULT 0', '').Replace(', `cardioDistanceMeters` REAL NOT NULL DEFAULT 0', '').Replace(', `cardioRounds` INTEGER NOT NULL DEFAULT 0', '')
}
$seed = @'
INSERT INTO food_records VALUES(5,'Old meal',230,NULL,20360,10);
INSERT INTO workout_records VALUES(1,'Old workout',20360,4,'Legacy detail',120,10);
INSERT INTO workout_plans VALUES(1,'Old plan',1,10);
INSERT INTO plan_exercises VALUES(1,1,'Old exercise',3,20,12,60,0);
INSERT INTO sleep_records VALUES(20360,7.5);
'@
$assertions = @'
CREATE TEMP TABLE assertions (ok INTEGER NOT NULL CHECK(ok=1));
INSERT INTO assertions SELECT COUNT(*)=1 FROM food_records WHERE id=5 AND calories=230 AND proteinGrams IS NULL AND carbsGrams IS NULL AND fatGrams IS NULL;
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_records WHERE id=1 AND summary='Legacy detail' AND sessionKey IS NULL AND volumeKg IS NULL;
INSERT INTO assertions SELECT COUNT(*)=1 FROM sleep_records WHERE hours=7.5;
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE reps=12 AND weightKg=20 AND isBodyweight=0 AND muscleGroup='' AND iconPath='' AND videoUrl='' AND isCardio=0 AND setTargetsJson='[]';
INSERT INTO plan_exercises VALUES(2,1,'Custom bodyweight',3,5,12,60,1,1,'Back','/private/icon.png','https://b23.tv/example',0,'[]','{}');
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE name='Custom bodyweight' AND isBodyweight=1 AND weightKg=5 AND muscleGroup='Back' AND iconPath='/private/icon.png' AND videoUrl='https://b23.tv/example';
INSERT INTO plan_exercises VALUES(3,1,'Custom cardio',1,0,10,0,2,0,'Back,Core','','',1,'[]','{"mode":"distance","meters":1000}');
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE name='Custom cardio' AND isCardio=1 AND isBodyweight=0;
INSERT INTO workout_records VALUES(2,'Another legacy',20360,1,'Legacy 2',20,20,NULL,NULL,0,0,0);
INSERT INTO workout_records VALUES(3,'New session',20360,2,'New detail',1800,30,'same-session',1060,1200,1000,1);
INSERT OR IGNORE INTO workout_records VALUES(4,'Retry session',20360,2,'New detail',1800,30,'same-session',1060,1200,1000,1);
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_records WHERE sessionKey='same-session' AND volumeKg=1060;
INSERT INTO assertions SELECT COUNT(*)=2 FROM workout_records WHERE sessionKey IS NULL;
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_records WHERE id=1 AND cardioSeconds=0 AND cardioDistanceMeters=0 AND cardioRounds=0;
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE id=1 AND cardioJson='{}';
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE id=3 AND muscleGroup='Back,Core' AND cardioJson='{"mode":"distance","meters":1000}';
UPDATE workout_records SET cardioSeconds=1500,cardioDistanceMeters=1250 WHERE id=3;
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_records WHERE id=3 AND sessionKey='same-session' AND volumeKg=1060 AND cardioSeconds=1500 AND cardioDistanceMeters=1250;
UPDATE plan_exercises SET setTargetsJson='[{"weight":40,"reps":10,"rest":0},{"weight":30,"reps":12,"rest":0}]' WHERE id=2;
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE id=2 AND setTargetsJson='[{"weight":40,"reps":10,"rest":0},{"weight":30,"reps":12,"rest":0}]';
DELETE FROM workout_plans WHERE id=1;
INSERT INTO assertions SELECT COUNT(*)=0 FROM plan_exercises;
INSERT INTO assertions SELECT COUNT(*)=3 FROM workout_records;
'@
# Run the production DAO update statements against a populated migrated database.
$planDao = [IO.File]::ReadAllText((Join-Path $workspace 'app/src/main/java/com/fitlog/app/data/Daos.kt'))
$updatePlanSql = [regex]::Match($planDao, '@Query\("(UPDATE workout_plans[^"]+)"\)').Groups[1].Value
$deleteExercisesSql = [regex]::Match($planDao, '@Query\("(DELETE FROM plan_exercises[^"]+)"\)').Groups[1].Value
if (-not $updatePlanSql -or -not $deleteExercisesSql) { throw 'Missing production plan update statements.' }
$updatePlanSql = $updatePlanSql.Replace(':name', "'Edited plan'").Replace(':count', '2').Replace(':id', '1')
$deleteExercisesSql = $deleteExercisesSql.Replace(':id', '1')
$replacePlanSql = @"
$updatePlanSql;
$deleteExercisesSql;
INSERT INTO plan_exercises(id,planId,name,targetSets,weightKg,reps,restSeconds,orderIndex,isBodyweight,muscleGroup,iconPath,videoUrl,isCardio,setTargetsJson,cardioJson)
VALUES(4,1,'Edited strength',2,25,8,90,0,0,'Chest,Arms','/private/new.png','https://b23.tv/edited',0,'[{"weight":25,"reps":8,"rest":90},{"weight":20,"reps":10,"rest":60}]','{}');
INSERT INTO plan_exercises(id,planId,name,targetSets,weightKg,reps,restSeconds,orderIndex,isBodyweight,muscleGroup,iconPath,videoUrl,isCardio,setTargetsJson,cardioJson)
VALUES(5,1,'Edited cardio',1,0,0,30,1,0,'Full body','','',1,'[]','{"mode":"distance","meters":1000}');
"@
$planAssertions = @"
SAVEPOINT cancelled_plan_edit;
$replacePlanSql
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_plans WHERE id=1 AND name='Edited plan' AND exerciseCount=2 AND createdAt=10;
INSERT INTO assertions SELECT COUNT(*)=2 FROM plan_exercises WHERE planId=1;
ROLLBACK TO cancelled_plan_edit;
RELEASE cancelled_plan_edit;
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_plans WHERE id=1 AND name='Old plan' AND createdAt=10;
INSERT INTO assertions SELECT COUNT(*)=3 FROM plan_exercises WHERE planId=1;
SAVEPOINT saved_plan_edit;
$replacePlanSql
RELEASE saved_plan_edit;
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_plans WHERE id=1 AND name='Edited plan' AND exerciseCount=2 AND createdAt=10;
INSERT INTO assertions SELECT COUNT(*)=2 FROM plan_exercises WHERE planId=1;
INSERT INTO assertions SELECT COUNT(*)=0 FROM plan_exercises WHERE id IN(1,2,3);
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE id=4 AND weightKg=25 AND muscleGroup='Chest,Arms' AND videoUrl='https://b23.tv/edited' AND setTargetsJson='[{"weight":25,"reps":8,"rest":90},{"weight":20,"reps":10,"rest":60}]';
INSERT INTO assertions SELECT COUNT(*)=1 FROM plan_exercises WHERE id=5 AND isCardio=1 AND cardioJson='{"mode":"distance","meters":1000}';
INSERT INTO assertions SELECT COUNT(*)=1 FROM workout_records WHERE id=1 AND summary='Legacy detail';
"@
$assertions = $assertions.Replace('DELETE FROM workout_plans WHERE id=1;', "$planAssertions`nDELETE FROM workout_plans WHERE id=1;")
$inspect = @'
SELECT 'food|'||name||'|'||type||'|'||"notnull"||'|'||pk FROM pragma_table_info('food_records') ORDER BY cid;
SELECT 'workout|'||name||'|'||type||'|'||"notnull"||'|'||pk FROM pragma_table_info('workout_records') ORDER BY cid;
SELECT 'plan|'||name||'|'||type||'|'||"notnull"||'|'||pk FROM pragma_table_info('workout_plans') ORDER BY cid;
SELECT 'exercise|'||name||'|'||type||'|'||"notnull"||'|'||pk||'|'||COALESCE(dflt_value,'NULL') FROM pragma_table_info('plan_exercises') ORDER BY cid;
SELECT 'sleep|'||name||'|'||type||'|'||"notnull"||'|'||pk FROM pragma_table_info('sleep_records') ORDER BY cid;
SELECT 'index|'||name||'|'||"unique" FROM pragma_index_list('workout_records') ORDER BY name;
'@
$prefix = ".bail on`nPRAGMA foreign_keys=ON;`n"
$migratedSql = $prefix + ($legacy -join "`n") + "`n" + $seed + "`n" + ($migrations -join "`n") + "`n" + $assertions + "`n" + $inspect
$actual = $migratedSql | & $sqlite ':memory:'
if ($LASTEXITCODE -ne 0) { throw 'Migration, preservation or idempotency regression failed.' }
$expected = ($prefix + ($creates -join "`n") + "`n" + $inspect) | & $sqlite ':memory:'
if ($LASTEXITCODE -ne 0 -or ($actual -join "`n") -ne ($expected -join "`n")) { throw 'Migrated schema differs from generated Room schema.' }
Write-Output 'SQLite: v3 -> v11 matches Room schema; historical data, cardio targets/metrics, multiple muscle groups, idempotent sessions, transactional plan editing/rollback with stable IDs, and cascading plan deletion verified.'
