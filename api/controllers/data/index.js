'use strict';

const HELPER_BASE = process.env.HELPER_BASE || '../../helpers/';
const Response = require(HELPER_BASE + 'response');
const Redirect = require(HELPER_BASE + 'redirect');

const MYSPOTS_FILEPATH = process.env.MYSPOTS_FILEPATH || './data/myspots.json';
const CHECKPOINTS_FILEPATH = process.env.CHECKPOINTS_FILEPATH || './data/checkpoints.json';
const ORIENTATION_FILEPATH = process.env.ORIENTATION_FILEPATH || './data/orientation.json';

const fs = require('fs');

try {
  fs.statSync(MYSPOTS_FILEPATH);
} catch (error) {
  fs.writeFileSync(MYSPOTS_FILEPATH, JSON.stringify([]), 'utf8');
}

try {
  fs.statSync(CHECKPOINTS_FILEPATH);
} catch (error) {
  fs.writeFileSync(CHECKPOINTS_FILEPATH, JSON.stringify([]), 'utf8');
}

try {
  fs.statSync(ORIENTATION_FILEPATH);
} catch (error) {
  fs.writeFileSync(ORIENTATION_FILEPATH, JSON.stringify({}), 'utf8');
}

exports.handler = async (event, context, callback) => {
  if( event.path == '/get-data'){
    var body = JSON.parse(event.body);
    var data;
    if( body.type == 'myspots' )
      data = JSON.parse(fs.readFileSync(MYSPOTS_FILEPATH, 'utf8'));
      else  if( body.type == 'checkpoints' )
      data = JSON.parse(fs.readFileSync(CHECKPOINTS_FILEPATH, 'utf8'));
    else  if( body.type == 'orientation' )
      data = JSON.parse(fs.readFileSync(ORIENTATION_FILEPATH, 'utf8'));

    return new Response({ status: 'OK', result: { data: data } });
  }else
  if( event.path == '/update-data'){
    var body = JSON.parse(event.body);
    if( body.type == 'myspots' )
      fs.writeFileSync(MYSPOTS_FILEPATH, JSON.stringify(body.data, 'utf8'));
    else if( body.type == 'checkpoints' )
      fs.writeFileSync(CHECKPOINTS_FILEPATH, JSON.stringify(body.data, 'utf8'));
    else if( body.type == 'orientation' )
      fs.writeFileSync(ORIENTATION_FILEPATH, JSON.stringify(body.data, 'utf8'));

    return new Response({ status: 'OK' });
  }
};
