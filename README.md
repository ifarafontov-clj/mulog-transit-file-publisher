# mulog-transit-publisher
Hello! Here is my take on rolling file publisher for and AWESOME mulog library (https://github.com/BrunoBonacci/mulog) </br>
It uses cognitec Transit formatter (https://github.com/cognitect/transit-clj) to store log records as Clojure data.</br>
It supports log rotation based on maximum size or maximum age of a file.</br>

## Installation

clj -e "(compile 'ifarafontov.NoopFlushOutputStream)"

Download from https://github.com/ifarafontov/transit-publisher.

## Usage

You can start the publisher using the following code:

```clojure
  (mu/start-publisher!
   {:type :custom
    :fqn-function "ifarafontov.transit-publisher/transit-file-publisher"
    :file-path "logz/log.json"
    :rotate-size {:mb 10}
    })

```
Accepted arguments:
| Key      |Default value |Description |
| ---------|--------------|------------|
| :file-path |"./app.log.json" |A path to logs directory, including file name. As there is no garanty in getting file's creation time on all filesystems (i.e. **ext4**) in Java - I decided to use file name for storing it. A **current** active log name will be formed as concatenation of: number of milliseconds from the epoch of 1970-01-01T00:00:00Z., an underscore character, and the file name (last element) from **:file-path** argument. Example: _1604991687740_log.json_ <br/> When the file will be rotated - it will be given a name consisting of a file name from **file-path** element, a dot character, and a current **local** date presented as "YYYYMMdd_hhmmss" string. Example: _log.json.20201110_115350_ </br> |
| :rotate-age  | nil (no rotation)  |A map with keyset #{:seconds, :minutes, :hours, :days, :weeks} and a positive integer values. Example: _{:seconds 2 :days 5}_ </br> In case of and unexpected key or a non positive integer value - will throw AssetionError|
| :rotate-size  | nil (no rotation)  |A map with keyset #{:kb, :mb, :gb } and a positive integer values. Example: _{:kb 2 :gb 5}_ </br> In case of and unexpected key or a non positive integer value - will throw AssetionError|
| :transit-format  | :json  |one of :json :json-verbose :msgpack. Check Transit documentation.|
| :transit-handlers  | nil |A map of custom transit handlers. Check Transit documentation. Also see **empty-folder-test** in ifarafontov.transit-publisher-no-rotate-integration-test namespace|
| :transform | identity |A user-defined function to be applied to log records before writing to file. Receives a single log entry map. Can be used for mapping  and/or filtering. Return **nil** if you do not want to log a particular record. Example: (do not log records with **:dont-log** key) </br>``` #(when-not (:dont-log (set (keys %))) %)  ``` |

## Notes:
* Throwables will be converted to maps via Throwable->Map </br>
* Transform function will be applied **before** converting Throwables - so you can provide custom conversion if needed
* There is a handy method **read-all-transit** that can be used for reading log files in **ifarafontov.transit-publisher** namespace. It expects **file-name**,
**transit-fromat** and **transit-handlers** (check arguments description in the table above).

Copyright © 2020 ifarafontov-clj

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
