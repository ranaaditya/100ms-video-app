var jwt = require('jsonwebtoken');
var uuid4 = require('uuid4');

var customer_id = "600e5b80a4a6804470252623";
var management_secret = "u9Jl5jHaT_auKVKBP_RgHUonZ97ZBc7Csz3Jso3d5WCDhxXRF_c5oltiUGqypRVWmkkml-XjYIXPU3-vjxiYV-dNeYQPLIEZsVEJyckRc9obHkPWP8d-CyZ3bR1fi1FqiM10rtlbGC4UrHrYKWqT4f_lPs2FGzl0aTUJsriSGgs=";
var management_key = "600e5b80a4a6804470252624";

jwt.sign({access_key: management_key}, 
				 management_secret, 
				 {algorithm: 'HS256', expiresIn: '24h', issuer: customer_id, jwtid: uuid4()},
				 function(err, token) {
						console.log(token);
				});

