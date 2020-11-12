# mulog-transit-publisher
Hello! Here is my take on rolling file publisher for and AWESOME mulog library (https://github.com/BrunoBonacci/mulog) </br>
It uses cognitec Transit formatter (https://github.com/cognitect/transit-clj) to store log entites as Clojure data.</br>
It supports log rotation based on maximum size or maximum age of a file.</br>

## Installation

clj -e "(compile 'ifarafontov.NoopFlushOutputStream)"

Download from https://github.com/ifarafontov/transit-publisher.

## Usage

You can start the publisher using the following code:

```clojure
  (mu/start-publisher!
   {:type :custom
    :fqn-function "ifarafontov.transit-publisher/transit-rolling-file-publisher"
    :file-path "logz/log.json"
    :rotate-size {:mb 10}
    })

```
Accepted arguments:
| Key      |Default value |Description |
| ---------|--------------|------------|
| :file-path |"./app.log.json" |A path to logs directory, including file name. As there is no garanty in getting file's creation time on all filesystems (i.e. **ext4**) in Java - I decided to use file name for storing it. A **current** active log name will be formed as concatenation of: number of milliseconds from the epoch of 1970-01-01T00:00:00Z., an underscore character, and the file name (last element) from **:file-path** argument. Example: _1604991687740_log.json_ <br/> When the file will be rotated - it will be given a name consistiong of a file name from **file-path** element, a dot character, and a current **local** date presented as "YYYYMMdd_hhmmss" string. Example: _log.json.20201110_115350_ </br> |
| :rotate-age  | nil (no rotation)  |A map with keyset #{:seconds, :minutes, :hours, :days, :weeks} and a positive integer values. Example: _{:seconds 2 :days 5}_ </br> In case of and unexpected key or a non positive integer value - will throw AssetionError|
| :rotate-size  | nil (no rotation)  |A map with keyset #{:kb, :mb, :gb } and a positive integer values. Example: _{:kb 2 :gb 5}_ </br> In case of and unexpected key or a non positive integer value - will throw AssetionError|
| :transit-format  | :json  |one of :json :json-verbose :msgpack. Check Transit documentation.|
| :transit-handlers  | nil |A map of custom transit handlers. Check Transit documentation. Also see **empty-folder-test** in ifarafontov.transit-publisher-no-rotate-integration-test namespace|
| :transform | identity |A user-defined function to be applied to log entries before writing to file. Receives a single log entry map. Can be used for mapping  and/or filtering. Return **nil** if you do not want to log a particular entry. Example: (do not log entries with **:dont-log** key) </br>``` #(when-not (:dont-log (set (keys %))) %)  ``` |

Run the project directly:

    $ clojure -m ifarafontov.transit-publisher

Run the project's tests (they'll fail until you edit them):

    $ clojure -A:test:runner -M:runner

Build an uberjar:

    $ clojure -A:uberjar -M:uberjar

Run that uberjar:

    $ java -jar transit-publisher.jar

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2020 Karhu

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
