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


var connectionService = function(){
    var seq = 0;
    //var connectionIdle = true;

    var connectParams = null;
    var socket = null;
    var paramsMap = {}
    var subsMap = {}

    var nextSeq = function() {
        var next = seq;
        seq += 1;
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

            /*var obj = {
                  subscriptions_added: {
                    "0": {
                      endpoint_set_prefix: [{
                        "part": []
                      }]
                    }
                  }
            };

            ws.send(JSON.stringify(obj))*/
        };

        ws.onmessage = function(message) {
            console.log(message);

            var json = JSON.parse(message.data);
            console.log(json);

            var subs = json['subscriptionNotification'];
            if (subs != null) {
                for (var key in subs) {
                    var subObj = paramsMap[key];
                    if (subObj != null) {
                        subObj.callback(subs[key]);
                    }
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
            ws.send(JSON.stringify(msg))
        }
        delete paramsMap[key];
    }

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
        }
    };
}();

angular.module('edgeGui', [ 'ngRoute' ])
    .config(function($routeProvider, $locationProvider) {
        $routeProvider
            .when('/other', { templateUrl: "other.html", controller: 'edgeOtherController' })
            .when('/main', { templateUrl: "main.html", controller: 'edgeMainController'  })
            .otherwise('/main')
    })
  .controller('edgeMainController', function($scope, $http, $interval, $location) {

    var me = i;
    i += 1;
    console.log("main controller: " + me);

    $scope.$on('$destroy', function() {
        console.log("main destroyed: " + me)
    });

  })
  .controller('edgeOtherController', function($scope, $http, $interval, $location) {
    console.log("other controller");
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