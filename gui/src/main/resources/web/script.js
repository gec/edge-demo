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

angular.module('edgeGui', [ 'ngRoute' ])
    .config(function($routeProvider, $locationProvider) {
        $routeProvider
            .when('/other', { templateUrl: "other.html" })
            .when('/main', { templateUrl: "main.html" })
            .otherwise('/main')
    })
  .controller('edgeGuiController', function($scope, $http, $interval, $location) {

    var connectionIdle = true;

    doConnect();

    $interval(check, 3000);

    function check() {
        if (connectionIdle) {
            doConnect();
        }
    }

    function doConnect() {

        var wsUri = $location.protocol() === 'https' ? 'wss' : 'ws';
        wsUri += '://' + $location.host() + ':' + $location.port();
        var ws = new WebSocket(wsUri + "/socket");
        connectionIdle = false;

        ws.onopen = function(){
            console.log("Socket has been opened!");
            console.log(ws);

            var obj = {
                subscription_request : {
                    content : {
                        endpoint_set_prefix : [{
                            part: [  ]

                        }]
                    }
                }
            };

            ws.send(JSON.stringify(obj))
        };

        ws.onmessage = function(message) {
            console.log(message);
            $scope.example = message.data;

            var json = JSON.parse(message.data);
            console.log(json);

            $scope.message = json;

            /*for (var key in json) {
                $scope[key] = json[key];
            }*/
            $scope.$digest();
        };

        ws.onerror = function(err) {
          console.log("error: " + err);

        };
        ws.onclose = function(ev) {
          console.log("onclose: " + ev);
          connectionIdle = true;
        };
    }

});