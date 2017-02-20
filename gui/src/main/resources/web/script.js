/*
 * Copyright 2011-2017 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

var i = 0;

function valueToJsValue(v) {

    for (var key in v) {
        if (key === 'uuidValue') {
            return v[key]; // NOTE: for now, just take object

        } else if (key === 'stringValue') {
            if (v[key]['mimeType'] != null) {
                return {
                    text: v[key].value,
                    mimeType: valueToJsValue(v[key]['mimeType'])
                };
            } else {
                return v[key].value;
            }

        } else if (key === 'bytesValue') {
            return v[key]; // NOTE: for now, just take object

        } else if (key === 'arrayValue') {
            return v[key].element.map(function(elem) {
                return valueToJsValue(elem);
            });

        } else if (key === 'objectValue') {
            var obj = {};
            var fields = v[key].fields;
            if (fields != null) {
                for (var k in fields) {
                    obj[k] = valueToJsValue(fields[k]);
                }
            }
            return obj;

        } else {
            return v[key];
        }
    }
}

function sampleValueToSimpleValue(v) {
    for (var key in v) {
        return v[key];
    }
}

function endpointIdForName(name) {
    return { namedId: { name: name } };
}
function endpointIdToString(id) {
    return id.namedId.name;
}

function pathToString(path) {
    var i = 0;
    var result = "";

    path.part.forEach(function(elem) {
        if (i != 0) {
            result = result + "/";
        }
        result = result + elem;
    })
    return result;
}

var connectionService = function(){
    var seq = 0;
    var outputSeq = 0;
    //var connectionIdle = true;

    var connectParams = null;
    var socket = null;
    var paramsMap = {};
    var subsMap = {};

    var correlationMap = {};
    var outputQueue = [];

    var nextSeq = function() {
        var next = seq;
        seq += 1;
        return next;
    }
    var nextOutputSeq = function() {
        var next = outputSeq;
        outputSeq += 1;
        return next;
    }

    //doConnect();

    function check() {
        if (connectParams != null && socket == null) {
            doConnect();
        }
    }

    function doConnect() {

        var wsUri = connectParams.protocol === 'https' ? 'wss' : 'ws';
        wsUri += '://' + connectParams.host + ':' + connectParams.port;
        var ws = new WebSocket(wsUri + "/socket");
        connectionIdle = false;

        ws.onopen = function(){
            console.log("Socket has been opened!");
            console.log(ws);
            socket = ws;
            doOnOpen();
        };

        ws.onmessage = function(message) {
            //console.log(message);

            var json = JSON.parse(message.data);
            //console.log(json);

            var subs = json['subscriptionNotification'];
            if (subs != null) {
                for (var key in subs) {
                    var subObj = paramsMap[key];
                    if (subObj != null) {
                        subObj.callback(subs[key]);
                    }
                }
            }

            var resp = json['outputResponse'];
            if (resp != null) {
                for (var corr in resp.results) {
                    var result = resp.results[corr];
                    var cb = correlationMap[corr];
                    if (cb != null) {
                        cb(result);
                    }
                    delete correlationMap[corr];
                }
            }

        };

        ws.onerror = function(err) {
          console.log("error: " + err);

        };
        ws.onclose = function(ev) {
          console.log("onclose: " + ev);
          socket = null;
        };
    }

    var doOnOpen = function() {
        for (var key in paramsMap) {
            var entry = paramsMap[key]
            doSubscription(key, entry.params, entry.callback);
        }
        for (var req in outputQueue) {
            doOutput(req.endPath, req.params, req.callback);
        }
    }

    var doSubscription = function(key, params, callback) {
        var subs = {};
        subs[key] = params;
        var msg = { subscriptions_added : subs }
        socket.send(JSON.stringify(msg))
    }

    var onRemove = function(key) {
        if (socket != null) {
            var msg = {
                subscriptions_removed: [ key ]
            }
            socket.send(JSON.stringify(msg))
        }
        delete paramsMap[key];
    }

    var doOutput = function(endPath, params, callback) {
        var correlation = nextOutputSeq();
        correlationMap[correlation] = callback;

        var msg = {
            outputRequest: {
                requests: [
                    {
                        key: endPath,
                        params: params,
                        correlation: correlation
                    }
                ]
            }
        };
        socket.send(JSON.stringify(msg))
    };

    return {
        start: function(connParams) {
            connectParams = connParams;
            connectParams.interval(check, 3000);
            check();
        },
        subscribe: function(par, cb) {
            var key = nextSeq();
            paramsMap[key] = { params: par, callback: cb };
            if (socket != null) {
                doSubscription(key, par, cb)
            }

            return {
                remove: function() {
                    onRemove(key);
                }
            }
        },
        outputRequest: function(endPath, params, callback) {
            if (socket != null) {
                doOutput(endPath, params, callback);
            } else {
                outputQueue.push({endPath: endPath, params: params, callback: callback});
            }
        }
    };
}();

var endpointInfo = function(id, desc) {

    var indexes = {}
    var metadata = {}

    if (desc.indexes != null) {
        desc.indexes.forEach(function(kv) {
            indexes[pathToString(kv.key)] = sampleValueToSimpleValue(kv.value);
        });
    }
    if (desc.metadata != null) {
        desc.metadata.forEach(function(kv) {
            metadata[pathToString(kv.key)] = valueToJsValue(kv.value);
        });
    }

    var dataCount = 0;
    var outputCount = 0;
    if (desc.dataKeySet != null) {
        dataCount = desc.dataKeySet.length;
    }
    if (desc.outputKeySet != null) {
        outputCount = desc.outputKeySet.length;
    }

    return {
        id: endpointIdToString(id),
        origEndpointId: id,
        indexes: indexes,
        metadata: metadata,
        dataCount: dataCount,
        outputCount: outputCount
    };
};

var nullDb = function() {
    return {
        currentValue: function() { return null; },
        observe: function(notification) {
            console.log("Unhandled data type: ")
            console.log(notification);
        }
    }
}

var kvDb = function(kvDesc, indexes, metadata) {

    var current = null;

    var handleSeqValue = function(v) {
        console.log("handleValue: ");
        console.log(v);

        var jsValue = valueToJsValue(v);
        console.log("JSVALUE:");
        console.log(jsValue);

        current = {
            type: 'latestKeyValue',
            value: v,
            jsValue: jsValue
        };
    };

    return {
        currentValue: function() {
            return current;
        },
        observe: function(notification) {
            console.log("KVDB notification: ");
            console.log(notification);

            if (notification.update != null) {
                var update = notification.update;
                if (update.sequencedValueUpdate != null) {
                    var value = update.sequencedValueUpdate.value;
                    if (value != null) {
                        handleSeqValue(value);
                    }
                }

            } else if (notification.state != null) {
                var state = notification.state;
                if (state.sequencedValueState != null) {
                    var value = state.sequencedValueState.value;
                    if (value != null) {
                        handleSeqValue(value);
                    }
                }
            }
        }
    }
}

var tsDb = function(tsDesc, indexes, metadata) {

    // TODO: caching, rotating store, etc...
    var current = null;

    var integerMap = null;
    if (metadata && metadata.integerMapping != null) {
        console.log("integer mapping: ");
        console.log(metadata.integerMapping);
        integerMap = {};
        metadata.integerMapping.forEach(function(elem) {
            integerMap[elem.index] = elem.name;
        });
    }

    var edgeSampleValueToJsSampleValue = function(v) {
        for (var k in v) {
            if (k === 'floatValue' || k === 'doubleValue') {
                return { decimal: v[k] }
            } else if (k === 'sint32Value' || k === 'uint32Value' || k === 'sint64Value' || k === 'uint64Value') {
                return { integer: v[k] }
            } else if (k === 'boolValue') {
                return { bool: v[k] }
            } else {
                console.log("Unrecognized sample value type: ");
                console.log(v);
                return null;
            }
        }
    }

    var handleTsSeq = function(tss) {

        var rawValue = tss.sample.value
        var v = sampleValueToSimpleValue(tss.sample.value);
        var t = tss.sample.time;
        var date = new Date(parseInt(t));

        var typedValue = edgeSampleValueToJsSampleValue(tss.sample.value)
        if (typedValue != null && typedValue.integer != null && integerMap != null && integerMap[typedValue.integer] != null) {
            typedValue = { string: integerMap[typedValue.integer] };
        }

        current = { type: 'timeSeriesValue', value: v, typedValue: typedValue, time: t, date: date };
    }

    return {
        currentValue: function() {
            return current;
        },
        observe: function(notification) {
//            console.log("TSDB notification: ");
//            console.log(notification);

            if (notification.update != null) {
                var update = notification.update;
                if (update.timeSeriesUpdate != null) {
                    var values = update.timeSeriesUpdate.values;
                    if (values != null) {
                        values.forEach(function (elem) {
//                            console.log("state elem: ");
//                            console.log(elem);
                            handleTsSeq(elem);
                        });
                    }
                }

            } else if (notification.state != null) {
                var state = notification.state;
                if (state.timeSeriesState != null) {
                    var values = state.timeSeriesState.values;
                    if (values != null) {
                        values.forEach(function (elem) {
//                            console.log("update elem: ");
//                            console.log(elem);
                            handleTsSeq(elem);
                        });
                    }
                }
            }

        }
    }
};

var dataObject = function(endpointId, key, desc, dbParam) {

    var name = pathToString(key);
    var indexes = null;
    var metadata = null;

    if (desc.indexes != null) {
        desc.indexes.forEach(function(kv) {
            if (indexes == null) { indexes = {}; }
            indexes[pathToString(kv.key)] = sampleValueToSimpleValue(kv.value);
        });
    }
    if (desc.metadata != null) {
        desc.metadata.forEach(function(kv) {
            if (metadata == null) { metadata = {}; }
            metadata[pathToString(kv.key)] = valueToJsValue(kv.value);
        });
    }

    var type = null;
    var db = nullDb();
    if (dbParam) {
        db = dbParam;
    } else {
        if (desc['timeSeriesValue'] != null) {
            db = tsDb(desc['timeSeriesValue'], indexes, metadata);
            type = 'timeSeriesValue';
        } else if (desc['latestKeyValue']) {
            db = kvDb(desc['latestKeyValue'], indexes, metadata);
            type = 'latestKeyValue';
        } else {
            console.log("unhandled desc:")
            console.log(desc);
        }
    }

    return {
        endpointId: endpointId,
        name : name,
        key: key,
        type: type,
        indexes: indexes,
        metadata: metadata,
        db: db
    };
};

var outputObject = function(endpointId, key, desc) {

    var indexes = null;
    var metadata = null;

    if (desc.indexes != null) {
        desc.indexes.forEach(function(kv) {
            if (indexes == null) { indexes = {}; }
            indexes[pathToString(kv.key)] = sampleValueToSimpleValue(kv.value);
        });
    }
    if (desc.metadata != null) {
        desc.metadata.forEach(function(kv) {
            if (metadata == null) { metadata = {}; }
            metadata[pathToString(kv.key)] = valueToJsValue(kv.value);
        });
    }

    var inputDef = null;

    if (metadata != null) {
        var simpleInputType = metadata['simpleInputType'];
        if (simpleInputType != null) {
            if (simpleInputType === 'integer') {

                var mapping = metadata['integerMapping']
                if (mapping != null) {
                    console.log("MAPPING: ");
                    console.log(mapping);
                    inputDef = { type: 'integer', mapping: mapping };
                } else {
                    inputDef = { type: 'integer' };
                }

            } else if (simpleInputType === 'double') {
                inputDef = { type: 'double' };

            } else if (simpleInputType === 'indication') {
                inputDef = { type: 'indication' };
            }
        }
    }

    return {
        endpointId: endpointId,
        key: key,
        indexes: indexes,
        metadata: metadata,
        inputDef: inputDef
    };
}

angular.module('edgeGui', [ 'ngRoute' ])
    .config(function($routeProvider, $locationProvider) {
        $routeProvider
            .when('/endpoint/:name', { templateUrl: "endpoint.html", controller: 'edgeEndpointController' })
            .when('/main', { templateUrl: "main.html", controller: 'edgeMainController'  })
            .otherwise('/main')
    })
  .controller('edgeMainController', function($scope, $http, $interval, $location) {
    console.log("main controller");

    $scope.$on('$destroy', function() {
        console.log("main destroyed: " + me)
    });

  })
  .controller('edgeEndpointController', function($scope, $routeParams, $http, $interval, $location) {
    console.log("endpoint controller for " + $routeParams);
    console.log($routeParams);

    var name = $routeParams.name;
    $scope.name = name;
    //$scope.dataTable = [];
    //$scope.outputTable = [];

    $scope.dataMap = {};
    $scope.timeSeriesArray = null;
    $scope.latestKeyValueArray = null;

    $scope.outputMap = {};

    $scope.endpointInfo = null;

    var updateDataTables = function() {
        var ts = [];
        var kv = [];

        for (var key in $scope.dataMap) {
            var data = $scope.dataMap[key]
            var type = data.type;
            if (type != null) {
                if (type === 'latestKeyValue') {
                    kv.push(data);
                } else if (type === 'timeSeriesValue') {
                    ts.push(data);
                }
            }
        }

        if (ts.length > 0) {
            $scope.timeSeriesArray = ts;
        }
        if (kv.length > 0) {
            $scope.latestKeyValueArray = kv;
        }
    };

    $scope.valueIsComplex = function(v) {
        return typeof v === 'object';
    }
    $scope.valueIsLong = function(v, len) {
        return typeof v === 'string' && v.length > len
    }

    $('#metadataModal').on('show.bs.modal', function (event) {
        console.log("saw modal event");
        var button = $(event.relatedTarget); // Button that triggered the modal
        $scope.modalKey = button.data('key');
        $scope.$digest();
    });

    $('#keyValueObjectModal').on('show.bs.modal', function (event) {
        console.log("saw kv modal event");
        var button = $(event.relatedTarget); // Button that triggered the modal
        $scope.modalKey = button.data('key');
        $scope.modalValue = $scope.dataMap[button.data('key')].db.currentValue();
        $scope.$digest();
    });

    $('#keyValueTextModal').on('show.bs.modal', function (event) {
        console.log("saw kv text modal event");
        var button = $(event.relatedTarget); // Button that triggered the modal
        $scope.modalKey = button.data('key');
        $scope.modalValue = $scope.dataMap[button.data('key')].db.currentValue();
        $scope.$digest();
    });

    $scope.issueDoubleOutput = function(key, outputObj) {

        console.log("issueDoubleOutput");
        console.log(key);
        console.log(outputObj);

        var outputValue = Number(outputObj.userOutput);

        var endPath = {
            endpointId: endpointIdForName(outputObj.endpointId),
            key: outputObj.key
        };

        var params = {
            output_value: {
                double_value: outputValue
            }
        };

        connectionService.outputRequest(endPath, params, function(result) {
            console.log("RESULT:")
            console.log(result);
        });
    };

    $scope.issueIndicationOutput = function(key, outputObj) {

        console.log("issueIndicationOutput");
        console.log(key);
        console.log(outputObj);

        var endPath = {
            endpointId: endpointIdForName(outputObj.endpointId),
            key: outputObj.key
        };

        var params = {};

        connectionService.outputRequest(endPath, params, function(result) {
            console.log("RESULT:")
            console.log(result);
        });
    };

    $scope.issueIntegerOutput = function(key, outputObj) {

        console.log("issueIntegerOutput");
        console.log(key);
        console.log(outputObj);

        var outputValue = Number(outputObj.userOutput);

        var endPath = {
            endpointId: endpointIdForName(outputObj.endpointId),
            key: outputObj.key
        };

        var params = {
            output_value: {
                sint64_value: outputValue
            }
        };

        connectionService.outputRequest(endPath, params, function(result) {
            console.log("RESULT:")
            console.log(result);
        });
    };

    var dataMap = {};
    //var outputMap = {};

    var keySub = null;

    var infoParams = {
        infoSubscription: [ endpointIdForName(name) ]
    }
    var infoSub = connectionService.subscribe(infoParams, function(msg) {
        console.log("got info: ");
        console.log(msg);

        //$scope.dataKeySet = [];
        //$scope.outputKeySet = [];
        var dataKeys = [];
        var outputKeys = [];

        msg.descriptorNotification.forEach(function(descNot) {
            console.log(descNot)
            var dataKs = [];
            var outputKs = [];
            var endId = descNot.endpointId
            var descriptor = descNot.endpointDescriptor;
            if (descriptor != null && endId != null) {

                $scope.endpointInfo = endpointInfo(endId, descriptor);

                if (descriptor.dataKeySet != null) {
                    descriptor.dataKeySet.forEach(function(elem) {
                        console.log("ELEM:");
                        console.log(elem);

                        var indexes = {};
                        var metadata = {};

                        if (elem.value.indexes != null) {
                            elem.value.indexes.forEach(function(kv) {
                                indexes[pathToString(kv.key)] = sampleValueToSimpleValue(kv.value);
                            });
                        }
                        if (elem.value.metadata != null) {
                            elem.value.metadata.forEach(function(kv) {
                                console.log(elem.metadata);
                                metadata[pathToString(kv.key)] = valueToJsValue(kv.value);
                            });
                        }

                        var endPath = { endpointId: endId, key: elem.key }
                        dataKs.push(endPath)

                        var pathStr = pathToString(elem.key);
                        var db = null;
                        var existing = dataMap[pathStr];
                        if (existing != null && existing.value != null) {
                            db = existing.value
                        }
                        var data = dataObject(name, elem.key, elem.value, db);
                        $scope.dataMap[pathStr] = data;
                    });
                }
                if (descriptor.outputKeySet != null) {
                    descriptor.outputKeySet.forEach(function(elem) {
                        console.log(elem);

                        var output = outputObject(name, elem.key, elem.value);
                        $scope.outputMap[pathToString(elem.key)] = output;

                        var endPath = { endpointId: endId, key: elem.key }
                        outputKs.push(endPath)
                    });
                }

                //$scope.dataKeySet = descriptor.dataKeySet;
                //$scope.outputKeySet = descriptor.outputKeySet;
            }

            dataKeys = dataKs;
            outputKeys = outputKs;
        });

        // TODO: remove removed keys

        updateDataTables();
        updateKeySub(dataKeys, outputKeys);

        $scope.$digest();
    });

    var updateKeySub = function(dataKeys, outputKeys) {
        if (keySub != null) {
            keySub.remove();
        }

        var keyParams = {
            dataSubscription: dataKeys,
            outputSubscription: outputKeys
        };
        console.log(keyParams);
        keySub = connectionService.subscribe(keyParams, function(msg) {
            //console.log("got data: ");
            //console.log(msg);

            var dataNotification = msg.dataNotification;
            if (dataNotification != null) {
                dataNotification.forEach(function(elem) {
                    var pathStr = pathToString(elem.key.key);
                    //console.log("NOTIFICATION: ");
                    //console.log(elem.value);
                    var dataObj = $scope.dataMap[pathStr];
                    if (dataObj != null) {
                        dataObj.db.observe(elem.value);
                    }
                });
            }

            $scope.$digest();
        });
    };


    $scope.$on('$destroy', function() {
        console.log("endpoint destroyed: " + name)
        infoSub.remove();
        if (keySub != null) {
            keySub.remove();
        }
    });

  })
  .controller('edgeGuiController', function($scope, $http, $interval, $location) {
    console.log("gui controller")


    $scope.manifest = []

    connectionService.start({
        protocol: $location.protocol(),
         host: $location.host(),
         port: $location.port(),
         interval: $interval
    });

    var params = {
               endpoint_set_prefix: [{
                 "part": []
               }]
             };

    var sub = connectionService.subscribe(params, function(msg) {
        console.log("Got subscription notification: ");
        console.log(msg);

        if (msg.endpointSetNotification != null) {
            for (var i in msg.endpointSetNotification) {
                var notification = msg.endpointSetNotification[i];
                console.log(notification);
                if (notification.snapshot != null) {
                    onSetSnapshot(notification.snapshot);
                } else {

                }
            }
        }

        $scope.$digest();
    });

    var onSetSnapshot = function(snap) {
        console.log("on set snapshot");
        var arr = []
        if (snap.entries != null) {
           $scope.manifest = snap.entries;
        }
    }

});