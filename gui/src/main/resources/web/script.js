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

        } else if (key === 'textValue') {
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

function endpointIdForPath(path) {
    return { name: path };
}
function endpointIdForName(name) {
    return { name: stringToPath(name) };
}
function endpointIdToString(id) {
    return pathToString(id.name);
}
function endpointPathFromIdAndKey(endpointId, key) {
    return {
        endpointId: endpointId,
        key: key
    };
}


var endPathToObjKey = function(endPath) {
    return endpointIdToString(endPath.endpointId) + '$' + pathToString(endPath.key);
};
var objKeyToEndPath = function(objKey) {
    var split = objKey.split('$');
    var endIdPath = stringToPath(split[0]);
    var keyPath = stringToPath(split[1]);
    return endpointPathFromIdAndKey(endIdForPath(endIdPath), keyPath);
};


function stringToPath(str) {
    var arr = str.split('/');
    return {
        part: arr
    }
}

function pathToString(path) {
    var i = 0;
    var result = "";

    path.part.forEach(function(elem) {
        if (i != 0) {
            result = result + "/";
        }
        result = result + elem;
        i += 1;
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

        /*var wsUri = connectParams.protocol === 'https' ? 'wss' : 'ws';
        wsUri += '://' + connectParams.host + ':' + connectParams.port;
        var ws = new WebSocket(wsUri + "/socket");*/
        var wsUri = "ws://127.0.0.1:8080";
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
        console.log("DOING: " + key);
        console.log(params);
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
            outputRequests: [
                {
                    id: endPath,
                    request: params,
                    correlation: correlation
                }
            ]
        };

        /*var msg = {
            outputRequest: {
                requests: [
                    {
                        key: endPath,
                        params: params,
                        correlation: correlation
                    }
                ]
            }
        };*/
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

var outputHelpers = function() {

    return {
        issueDoubleOutput : function(key, outputObj) {

            console.log("issueDoubleOutput");
            console.log(key);
            console.log(outputObj);

            var outputValue = Number(outputObj.userOutput);

            var endPath = {
                endpointId: outputObj.endpointId,
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
        },

       issueIndicationOutput : function(key, outputObj) {

            console.log("issueIndicationOutput");
            console.log(key);
            console.log(outputObj);

            var endPath = {
                endpointId: outputObj.endpointId,
                key: outputObj.key
            };

            var params = {};

            connectionService.outputRequest(endPath, params, function(result) {
                console.log("RESULT:")
                console.log(result);
            });
        },

        issueIntegerOutput : function(key, outputObj) {

            console.log("issueIntegerOutput");
            console.log(key);
            console.log(outputObj);

            var outputValue = Number(outputObj.userOutput);

            var endPath = {
                endpointId: outputObj.endpointId,
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
        },
    }
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


var eventDb = function(desc, indexes, metadata) {

    var current = [];

    var handleEvents = function(arr) {
        arr.forEach(handleEvent);
    };

    var handleEvent = function(ev) {
        current.push({
            topicParts: ev.topic.part,
            value: valueToJsValue(ev.value),
            time: ev.time
        });
        if (current.length > 100) {
            current.shift();
        }
    };

    return {
        currentValue: function() {
            return current;
        },
        observe: function(notification) {
            console.log("EVENT notification: ");
            console.log(notification);

            if (notification.topicEventUpdate != null) {
                handleEvent(notification.topicEventUpdate);
            }
        }
    }
};

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

            if (notification.keyValueUpdate != null) {
                if (notification.keyValueUpdate.value != null) {
                    handleSeqValue(notification.keyValueUpdate.value);
                }
            }
        }
    }
};

var tsDb = function(tsDesc, indexes, metadata) {

    // TODO: caching, rotating store, etc...
    var current = null;

    var integerMap = null;
    if (metadata != null && metadata.integerMapping != null) {
        console.log("integer mapping: ");
        console.log(metadata.integerMapping);
        integerMap = {};
        metadata.integerMapping.forEach(function(elem) {
            integerMap[elem.index] = elem.name;
        });
    }

    var boolMap = null;
    if (metadata != null && metadata.boolMapping != null) {
        console.log("bool mapping: ");
        console.log(metadata.boolMapping);
        boolMap = {};
        metadata.boolMapping.forEach(function(elem) {
            if (elem.value != null) {
                boolMap[elem.value] = elem.name;
            }
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

        var v = sampleValueToSimpleValue(tss.value);
        var t = tss.time;
        var date = new Date(parseInt(t));

        var typedValue = edgeSampleValueToJsSampleValue(tss.value);
        if (typedValue != null && typedValue.integer != null && integerMap != null && integerMap[typedValue.integer] != null) {
            typedValue = { string: integerMap[typedValue.integer] };
        }
        if (typedValue != null && typedValue.bool != null && boolMap != null && boolMap[typedValue.bool] != null) {
            typedValue = { string: boolMap[typedValue.bool] };
        }

        current = { type: 'timeSeriesValue', value: v, typedValue: typedValue, time: t, date: date };
    }

    return {
        currentValue: function() {
            return current;
        },
        observe: function(updateWrap) {
            if (updateWrap.seriesUpdate != null) {
                var update = updateWrap.seriesUpdate;
                handleTsSeq(update);
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
        } else if (desc['eventTopicValue']) {
            db = eventDb(desc['eventTopicValue'], indexes, metadata);
            type = 'eventTopicValue';
        } else {
            console.log("unhandled desc:")
            console.log(desc);
        }
    }

    return {
        endpointId: endpointId,
        key: key,
        endPath: endpointPathFromIdAndKey(endpointId, key),
        name : name,
        type: type,
        indexes: indexes,
        metadata: metadata,
        db: db
    };
};

var outputObject = function(endpointId, key, desc) {

    var name = pathToString(key);
    var indexes = null;
    var metadata = null;

    console.log("OUTPUT DESC:");
    console.log(desc);

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

    var endpointPath = endpointPathFromIdAndKey(endpointId, key);

    return {
        endpointId: endpointId,
        key: key,
        endPath: endpointPath,
        endpointPathString: endPathToObjKey(endpointPath),
        name: name,
        indexes: indexes,
        metadata: metadata,
        inputDef: inputDef
    };
};

var endpointDescriptorSubscription = function(endId, handler) {

    var dataMap = {};

    var infoParams = {
        descriptors: [ endId ]
    };
    console.log("Subscribing descriptor: " + endId);
    console.log(infoParams);
    var infoSub = connectionService.subscribe(infoParams, function(msg) {
        console.log("endpointDescriptorSubscription got info: ");
        console.log(msg);

        var dataObjects = [];
        var outputObjects = [];
        var descResult = null;

        msg.updates.filter(function(v) { return v.endpointUpdate != null })
            .map(function(v) { return v.endpointUpdate })
            .forEach(function(update) {
                console.log(update);
                var endId = update.id;
                var descriptor = update.value;
                if (descriptor != null && endId != null) {

                    descResult = endpointInfo(endId, descriptor);

                    if (descriptor.dataKeySet != null) {
                        descriptor.dataKeySet.forEach(function(elem) {
                            console.log("ELEM:");
                            console.log(elem);

                            var pathStr = pathToString(elem.key);
                            var db = null;
                            var existing = dataMap[pathStr];
                            if (existing != null && existing.value != null) {
                                db = existing.value
                            }

                            //var dataObject = function(endpointId, key, desc, dbParam)
                            var data = dataObject(endId, elem.key, elem.value, db);
                            dataObjects.push(data)
                        });
                    }
                    if (descriptor.outputKeySet != null) {
                        descriptor.outputKeySet.forEach(function(elem) {
                            console.log("OutELEM:");
                            console.log(elem);

                            var output = outputObject(endId, elem.key, elem.value);
                            outputObjects.push(output)
                        });
                    }
                }
            });

        handler({
            descriptor: descResult,
            data: dataObjects,
            output: outputObjects
        });
    });

    return infoSub;
};


var dataIndexSubscription = function(spec, dataHandler) {
    var subParams = {
        indexParams: {
            data_key_indexes: [ spec ]
        }
    };
    var sub = connectionService.subscribe(subParams, function(msg) {
        console.log("dataIndexSubscription got sub: ");
        console.log(msg);

        if (msg.indexNotification != null && msg.indexNotification.dataKeyNotifications != null) {
            var not = msg.indexNotification.dataKeyNotifications
            not.forEach(function(elem) {
                if (elem.snapshot != null && elem.snapshot.endpointPaths != null) {
                    var pathSet = elem.snapshot.endpointPaths
                    onSetUpdate(pathSet);
                }
            });
        }

    });

    var onSetUpdate = function(pathList) {
        console.log("pathList:");
        console.log(pathList);
        var keyParams = {
          dataSubscription: pathList
        };
        console.log("keyParams:");
        console.log(keyParams);

        keySub = connectionService.subscribe(keyParams, function(msg) {
          //console.log("got data for: " + spec);
          //console.log(msg);
          dataHandler(msg, pathList);
        });
    };

    return sub;
};

var outputIndexSubscription = function(spec, dataHandler) {
    var subParams = {
        indexParams: {
            output_key_indexes: [ spec ]
        }
    };
    var sub = connectionService.subscribe(subParams, function(msg) {
        console.log("GOT OUTPUT SUB: ");
        console.log(msg);

        if (msg.indexNotification != null && msg.indexNotification.outputKeyNotifications != null) {
            var not = msg.indexNotification.outputKeyNotifications
            not.forEach(function(elem) {
                if (elem.snapshot != null && elem.snapshot.endpointPaths != null) {
                    var pathSet = elem.snapshot.endpointPaths
                    onSetUpdate(pathSet);
                }
            });
        }

    });

    var onSetUpdate = function(pathList) {
        console.log("pathList:");
        console.log(pathList);
        var keyParams = {
          outputSubscription: pathList
        };
        console.log("keyParams:");
        console.log(keyParams);

        keySub = connectionService.subscribe(keyParams, function(msg) {
          console.log("got OUTPUT SUB FOR: " + spec);
          console.log(msg);
          dataHandler(msg, pathList);
        });
    }

    return sub;
};


angular.module('edgeGui', [ 'ngRoute' ])
    .config(function($routeProvider, $locationProvider) {
        $routeProvider
            .when('/endpoint', { templateUrl: "endpoint.html", controller: 'edgeEndpointController' })
            .when('/endpointdesc', { templateUrl: "endpointdesc.html", controller: 'edgeEndpointDescController' })
            .when('/main', { templateUrl: "main.html", controller: 'edgeMainController'  })
            .otherwise('/main')
    })
  .controller('edgeMainController', function($scope, $http, $interval, $location) {
    console.log("main controller");

    $scope.dataMap = {};
    $scope.outputMap = {};

    $scope.outputs = outputHelpers;

    //$scope.outputPowerSet = []

    var updateDataSet = function(name, pathList) {
        var set = [];
        pathList.forEach(function(endPath) {
            var key = endPathToObjKey(endPath);
            var dataObj = $scope.dataMap[key];
            if (dataObj) {
                set.push(dataObj)
            }
        });
        if (set.length > 0) {
            $scope[name] = set;
        }
    }

    var updateOutputSet = function(name, pathList) {
        var set = [];
        pathList.forEach(function(endPath) {
            var key = endPathToObjKey(endPath);
            var outputObj = $scope.outputMap[key];
            if (outputObj) {
                set.push(outputObj)
            }
        });
        if (set.length > 0) {
            $scope[name] = set;
        }
    }

    var handleNotification = function(msg) {
        var dataNotification = msg.dataNotification;
        if (dataNotification != null) {
            dataNotification.forEach(function(elem) {

                var endPath = elem.key;

                if (elem.descriptorNotification != null) {
                    var descNot = elem.descriptorNotification;
                    //console.log("DESC:");
                    //console.log(descNot);

                    handleDataKeyNotification(descNot.endpointPath, descNot.keyDescriptor);
                }

                var dataMapKey = endPathToObjKey(endPath);
                var dataObj = $scope.dataMap[dataMapKey];
                if (dataObj != null) {
                    //console.log("NOTIFICATION: ");
                    //console.log(elem);
                    dataObj.db.observe(elem.value);
                }
            });
        }
        var outputNotification = msg.outputNotification;
        if (outputNotification != null) {
            outputNotification.forEach(function(elem) {

                var endPath = elem.key;
                if (elem.descriptorNotification != null) {
                    var descNot = elem.descriptorNotification;
                    console.log("Output DESC:");
                    console.log(descNot);

                    handleOutputKeyNotification(descNot.endpointPath, descNot.keyDescriptor);
                }
            });
        }

        $scope.$digest();
    };


    var handleDataKeyNotification = function(endPath, keyDescriptor) {

        var dataMapKey = endPathToObjKey(endPath);

        var db = null;
        var existing = $scope.dataMap[dataMapKey];
        if (existing != null && existing.value != null) {
            db = existing.value
        }
        var data = dataObject(endPath.endpointId, endPath.key, keyDescriptor, db);

        $scope.dataMap[dataMapKey] = data;
    }


    var handleOutputKeyNotification = function(endPath, keyDescriptor) {
        console.log("handleOutputKeyNotification")

        var dataMapKey = endPathToObjKey(endPath);

        var output = outputObject(endPath.endpointId, endPath.key, keyDescriptor);
        $scope.outputMap[dataMapKey] = output;
    }


    /*var outputPowerSpec = {
        key: { part: [ 'gridValueType' ] },
        value: { stringValue: 'outputPower' }
    };

    var outputPowerSub = dataIndexSubscription(outputPowerSpec, function (msg, pathList) {
        handleNotification(msg)
        updateDataSet('outputPowerSet', pathList);
    });

    var breakerStatus = {
        key: { part: [ 'gridValueType' ] },
        value: { stringValue: 'breakerStatus' }
    };

    var breakerStatusSub = dataIndexSubscription(breakerStatus, function (msg, pathList) {
        handleNotification(msg)
        updateDataSet('breakerStatusSet', pathList);
    });

    var essModeSpec = {
        key: { part: [ 'gridValueType' ] },
        value: { stringValue: 'essMode' }
    };

    var essModeSub = dataIndexSubscription(essModeSpec, function (msg, pathList) {
        handleNotification(msg)
        updateDataSet('essModeSet', pathList);
    });

    var essSocSpec = {
        key: { part: [ 'gridValueType' ] },
        value: { stringValue: 'percentSoc' }
    };

    var essSocSub = dataIndexSubscription(essSocSpec, function (msg, pathList) {
        handleNotification(msg)
        updateDataSet('essSocSet', pathList);
    });

    var breakerOutputSpec = {
        key: { part: [ 'gridOutputType' ] },
        value: { stringValue: 'pccBkrSwitch' }
    };

    var breakerOutputSub = outputIndexSubscription(breakerOutputSpec, function (msg, pathList) {
        console.log("saw notification: ");
        console.log(msg);
        handleNotification(msg)
        updateOutputSet('breakerOutputSet', pathList);
    });

    var outputTargetSpec = {
        key: { part: [ 'gridOutputType' ] },
        value: { stringValue: 'setOutputTarget' }
    };

    var outputTargetSub = outputIndexSubscription(outputTargetSpec, function (msg, pathList) {
        console.log("saw notification: ");
        console.log(msg);
        handleNotification(msg)
        updateOutputSet('outputTargetSet', pathList);
    });


    var outputEssModeSpec = {
        key: { part: [ 'gridOutputType' ] },
        value: { stringValue: 'setEssMode' }
    };

    var outputEssModeSub = outputIndexSubscription(outputEssModeSpec, function (msg, pathList) {
        console.log("saw notification: ");
        console.log(msg);
        handleNotification(msg)
        updateOutputSet('setEssModeSet', pathList);
    });*/

    $scope.$on('$destroy', function() {
        console.log("main destroyed: ");
    });

  })
  .controller('edgeEndpointController', function($scope, $routeParams, $http, $interval, $location) {
    console.log("endpoint controller for " + $routeParams);
    console.log($routeParams);

    var name = $routeParams.name;

    var namePath = stringToPath(name);
    console.log("namePath");
    console.log(namePath);

    $scope.name = name;
    $scope.namePath = namePath;
    $scope.nameParts = namePath.part;
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
        var ev = [];

        for (var key in $scope.dataMap) {
            var data = $scope.dataMap[key]
            var type = data.type;
            console.log("DATA:");
            console.log(data);
            if (type != null) {
                if (type === 'latestKeyValue') {
                    kv.push(data);
                } else if (type === 'timeSeriesValue') {
                    ts.push(data);
                } else if (type === 'eventTopicValue') {
                    ev.push(data);
                }
            }
        }

        if (ts.length > 0) {
            $scope.timeSeriesArray = ts;
        }
        if (kv.length > 0) {
            $scope.latestKeyValueArray = kv;
        }
        if (ev.length > 0) {
            $scope.eventTopicValueArray = ev;
        }
    };

    $scope.valueIsComplex = function(v) {
        return typeof v === 'object';
    };
    $scope.valueIsLong = function(v, len) {
        return typeof v === 'string' && v.length > len
    };

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

    $scope.outputs = outputHelpers;

    var keySub = null;

    var dataArrayToDataSubParams = function(data) {

        var ts = [];
        var kv = [];
        var ev = [];

        data.forEach(function(obj) {
                if (obj.type === 'latestKeyValue') {
                    kv.push(obj.endPath);
                } else if (obj.type === 'timeSeriesValue') {
                    ts.push(obj.endPath);
                } else if (obj.type === 'eventTopicValue') {
                    ev.push(obj.endPath);
                }
        });

        return {
            series: ts,
            key_values: kv,
            topic_events: ev
        };
    };

    var descSub = endpointDescriptorSubscription(endpointIdForPath(namePath), function(result) {
        console.log("Got endpoint desc: " + namePath);
        console.log(result);
        result.data.forEach(function(elem) {
            $scope.dataMap[elem.name] = elem;
        });
        result.output.forEach(function(elem) {
            console.log("adding output");
            console.log(elem);
            $scope.outputMap[elem.name] = elem;
        });

        $scope.endpointInfo = result.descriptor;

        updateDataTables();

        var dataParams = dataArrayToDataSubParams(result.data);
        var outputKeys = result.output.map(function(out) { return out.endPath });

        updateKeySub(dataParams, outputKeys);

        $scope.$digest();
    });

    var updateKeySub = function(dataParams, outputKeys) {
        if (keySub != null) {
            keySub.remove();
        }

        var keyParams = {
            data_params: dataParams,
            output_keys: outputKeys
        };
        console.log(keyParams);
        keySub = connectionService.subscribe(keyParams, function(msg) {
            /*console.log("got data: ");
            console.log(msg);*/

            if (msg.updates != null) {

                var dataKeyUpdates = msg.updates.reduce(function(acc, elem) {
                    if (elem.dataKeyUpdate != null) acc.push(elem.dataKeyUpdate)
                    return acc;
                }, []);

                dataKeyUpdates.forEach(function(update) {
                    var pathStr = pathToString(update.id.key);
                    var dataObj = $scope.dataMap[pathStr];
                    if (dataObj != null) {
                        //console.log(dataObj);
                        //console.log(update.value);
                        dataObj.db.observe(update.value);
                    }
                });
            };

            $scope.$digest();
        });
    };


    $scope.$on('$destroy', function() {
        console.log("endpoint destroyed: " + name);
        descSub.remove();
        if (keySub != null) {
            keySub.remove();
        }
    });

  })
  .controller('edgeEndpointDescController', function($scope, $routeParams, $http, $interval, $location) {
      console.log("endpoint desc controller for " + $routeParams);
      console.log($routeParams);

      var name = $routeParams.name;

      var namePath = stringToPath(name);
      console.log(namePath);

      $scope.name = name;
      $scope.namePath = namePath;
      $scope.nameParts = namePath.part;

      $scope.dataMap = {};
      $scope.timeSeriesArray = null;
      $scope.latestKeyValueArray = null;

      $scope.outputMap = {};

      $scope.endpointInfo = null;


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

      $scope.outputs = outputHelpers;

      var descSub = endpointDescriptorSubscription(endpointIdForPath(namePath), function(result) {
          console.log("Got endpoint desc: " + namePath);
          console.log(result);
          result.data.forEach(function(elem) {
              $scope.dataMap[elem.name] = elem;
          });
          result.output.forEach(function(elem) {
              console.log("adding output");
              console.log(elem);
              $scope.outputMap[elem.name] = elem;
          });

          $scope.endpointInfo = result.descriptor;

          $scope.$digest();
      });

      $scope.$on('$destroy', function() {
          console.log("endpoint destroyed: " + name)
          descSub.remove();
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
        index_params: {
            endpoint_prefixes: [{
                "part": []
            }]
        }
    };

    var sub = connectionService.subscribe(params, function(msg) {
        console.log("Got subscription notification: ");
        console.log(msg);

        if (msg.updates != null) {
            for (var i in msg.updates) {
                var update = msg.updates[i];
                console.log(update);
                if (update.endpointPrefixUpdate != null) {
                    if (update.endpointPrefixUpdate.value != null) {
                        onSetSnapshot(update.endpointPrefixUpdate.value.value);
                    }
                }
            }
        }

        $scope.$digest();
    });

    var onSetSnapshot = function(set) {
        console.log("on set snapshot");
        console.log(set);
        var arr = [];
           //$scope.manifest = snap.entries;
        set.forEach(function(elem) {
            arr.push({
                simpleName: endpointIdToString(elem),
                nameParts: elem.name.part,
                endpointId: elem
            })
       });

        $scope.manifest = arr;
    }

});