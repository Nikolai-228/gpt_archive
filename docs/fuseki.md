# Fuseki

## Общее описание

Сервер Apache Jena Fuseki поддерживает интерфейс к набору данных RDF с
информацией о сущностях. Набор данных может быть постоянным (TDB, все изменения
сохраняются на диск) или работать в оперативной памяти. TDB используется в
рабочем окружении, а in-memory хранилище удобно при разработке и тестировании

Данные располагаются в нескольких _гра́фах_ RDF. В _графе по-умолчанию_
содержится информация о сущностях, созданных пользователями в системе
(фотографии, люди, организации). Интерфейс редактирования системы вносит правки
в этот граф. Остальные (именованные) графы содержат служебную информацию о
допустимых свойствах, типах и значениях.

По-умолчанию Fuseki даёт HTTP-интерфейс для доступа к набору данных через
язык запросов SPARQL. Также для реализации сценариев работы, предполагаемых
Электронным архивом, встроен модуль, обеспечивающий работу с сущностями
как древовидными структурами Clojure (интерфейс `IForest` на [схеме
развёртывания](./deploy.md)). Он представлен компонентом `istu.archive.fuseki`.

Также Fuseki автоматически поддерживает текстовый индекс Lucene, который
используется при поиске сущностей по названиям.

## Запуск и конфигурация

Fuseki написана на языке Java и требует наличия в системе установленной
виртуальной машины Java. Для запуска используется файл конфигурации, пример
которой дан ниже.

<details>
  <summary>Пример конфигурации (fuseki-config.ttl)</summary>

```ttl
@prefix :       <#>
@prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
@prefix rdfs:   <http://www.w3.org/2000/01/rdf-schema#>
@prefix ja:     <http://jena.hpl.hp.com/2005/11/Assembler#>
@prefix fuseki: <http://jena.apache.org/fuseki#>
@prefix tdb2:   <http://jena.apache.org/2016/tdb#>
@prefix text:   <http://jena.apache.org/text#>
@prefix ia:     <http://archive.istu.ru/ns#>
@prefix schema: <http://schema.org/>
@prefix skos:   <http://www.w3.org/2004/02/skos/core#>

[] a fuseki:Server
    ; fuseki:services ( :main )
    .
:main a fuseki:Service
    ; fuseki:name     "istu-archive"
    ; fuseki:endpoint [ fuseki:operation fuseki:query
                      ; fuseki:name      "query"
                      ; ja:context       [ ja:cxtName  "arq:queryTimeout"
                                         ; ja:cxtValue "10000"
                                         ]
                      ]
    ; fuseki:endpoint [ fuseki:operation fuseki:gsp-r ]
    ; fuseki:endpoint [ fuseki:operation ia:forest
                      ; fuseki:name      "forest"
                      ]
    ; fuseki:dataset  :textDataset
    .
:textDataset a text:TextDataset
    ; text:dataset :dataset  # нужно заменить на :tdbDataset, чтобы
                             # использовать TDB
    ; text:index   :lucene
    .
:tdbDataset a tdb2:DatasetTDB2
    ; tdb2:location "/home/kotwys/tdb"
    .
:dataset a ja:RDFDataset
    ; ja:defaultGraph [ a ja:MemoryModel; ja:content :test ]
    ; ja:namedGraph
        [ ja:graphName ia:
        ; ja:graph     [ a ja:MemoryModel
                       ; ja:content :istu , :uncertainty , :schema2
                       ]
        ]
    ; ja:namedGraph
        [ ja:graphName ia:i18n
        ; ja:graph     [ a ja:MemoryModel; ja:content :i18n ]
        ]
    ; ja:namedGraph
        [ ja:graphName ia:externalOntologies
        ; ja:graph     [ a ja:MemoryModel
                       ; ja:content :owl , :org
                       ]
        ]
    ; ja:namedGraph
        [ ja:graphName schema:
        ; ja:graph     [ a ja:MemoryModel; ja:content :schema ]
        ]
    .

:istu        ja:externalContent <file:./ontology/istu.ttl> .
:schema      ja:externalContent <file:./schemaorg-current-http.ttl> .
:schema2     ja:externalContent <file:./ontology/schema.ttl> .
:owl         ja:externalContent <http://www.w3.org/2002/07/owl#> .
:i18n        ja:externalContent <file:./ontology/i18n.ttl> .
:uncertainty ja:externalContent <file:./ontology/uncertainty.ttl> .
:org         ja:externalContent <http://www.w3.org/ns/org#> .
:time        ja:externalContent <http://www.w3.org/2006/time#> .
:exif        ja:externalContent <http://www.kanzaki.com/ns/exif#> .
:test        ja:externalContent <file:./test.schema.ttl> .

:lucene a text:TextIndexLucene
    ; text:directory   <file:/home/kotwys/lucene>
    ; text:entityMap   :luceneEntityMap
    ; text:analyzer    [ a              text:ConfigurableAnalyzer
                       ; text:tokenizer text:StandardTokenizer
                       ; text:filters   ( text:ASCIIFoldingFilter text:LowerCaseFilter )
                       ]
    ; text:storeValues true
    .
:luceneEntityMap a text:EntityMap
    ; text:defaultField "label"
    ; text:entityField  "uri"
    ; text:uidField     "uid"
    # ; text:langField    "lang"
    ; text:graphField   "graph"
    ; text:map          ( [ text:field     "label"
                          ; text:predicate schema:name
                          ]
                          [ text:field     "label"
                          ; text:predicate rdfs:label
                          ]
                          [ text:field     "label"
                          ; text:predicate skos:altLabel
                          ]
                          # Fake property so that we can match on the entity URI
                          [ text:field     "uri"
                          ; text:predicate schema:url
                          ]
                        )
    .
```

</details>

Если вы используете in-memory базу данных, то файл конфигурации должен
располагаться в корне данного репозитория для того, чтобы файлы онтологий были
корректно найдены.

Для запуска сервера Fuseki при наличии загруженного с официального сайта
JAR-архива необходимо выполнить следующую команду (на Windows разделитель внутри
`-cp` может отличаться). Исправьте пути к файлам, чтобы они соответствовали
расположению на вашей машине относительно рабочей директории. Вместо
`$MODULE_CP` укажите корректный путь к классам модуля (см. ниже).

```bash
java -cp jena-fuseki-server-5.2.0.jar:$MODULE_CP \
  org.apache.jena.fuseki.main.cmds.FusekiMainCmd \
  --config=fuseki-config.ttl \
  --modules=true
```

## Загрузка кода модуля

Clojure позволяет модифицировать и выполнять код без компиляции, что удобно во
время разработки. Однако, чтобы Java-код Fuseki обнаружил входную точку в код
модуля, необходимо один раз скомпилировать классы. Для этого в папке `fuseki`
выполните команду:

```bash
clj -M:compile -e "(binding [*warn-on-reflection* true *compile-path* \"build/classes\"] (compile 'istu.archive.fuseki.module))"
```

После этого в команде запуска Fuseki вместо `$MODULE_CP` можно указать
`$(clojure -Spath)`. В таком случае необходимо запускать Fuseki из той же папки
`fuseki`.

Компиляцию необходимо перевыполнять после любого изменения кода класса модуля. В
то же время изменения в обычных функциях Clojure будут отображаться сразу после
перезапуска Fuseki.

В рабочем окружении код модуля загружается в виде заранее скомпилированного
самостоятельного *über JAR*-файла. Для того, чтобы его создать, в папке `fuseki`
необходимо выполнить команду:

```bash
clj -T:build:compile uberjar
```

Готовый JAR-файл появится в папке `build/lib/`.

## Заполнение TDB

Если используется хранилище TDB, то служебные данные в именованных графах
необходимо загрузить вручную. Для этого необходимо выполнить следующую серию
команд. (Укажите корректный путь к JAR-файлу, директории TDB и локальным файлам
онтологии.)

```bash
java -cp jena-fuseki-server-5.2.0.jar tdb2.tdbloader \
     --loc=/home/archive/tdb \
     --graph="http://archive.istu.ru/ns#" \
     istu-photo-archive/ontology/istu.ttl \
     istu-photo-archive/ontology/schema.ttl \
     istu-photo-archive/ontology/uncertainty.ttl

java -cp jena-fuseki-server-5.2.0.jar tdb2.tdbloader \
    --loc=/home/archive/tdb \
    --graph="http://archive.istu.ru/ns#externalOntologies" \
    http://www.w3.org/2002/07/owl \
    http://www.w3.org/ns/org

java -cp jena-fuseki-server-5.2.0.jar tdb2.tdbloader \
     --loc=/home/archive/tdb \
     --graph="http://schema.org/" \
     https://schema.org/version/latest/schemaorg-current-http.ttl

java -cp jena-fuseki-server-5.2.0.jar tdb2.tdbloader \
     --loc=/home/archive/tdb \
     --graph="http://archive.istu.ru/ns#i18n" \
     istu-photo-archive/ontology/i18n.ttl
```

`tdbloader` только добавляет новые утверждения в графы. Если изменения онтологии
заключались в _удалении_ каких-либо утверждений, то может потребоваться полная
очистка соответствующего графа и повторная загрузка.
