'use strict';

//var vConsole = new VConsole();

const default_lat = 35.465878;
const default_lng = 139.622329;
const base_url = "http://localhost:10080";

var vue_options = {
    el: "#top",
    data: {
        progress_title: '', // for progress-dialog

        origin_index: 0, // 出発のインデックス
        destination_completed : true, // チェックポイントに到着したかどうか
        checkpoints: [], // チェックポイントのリスト
        map_markers: [], // Map1に配置のマーカ
        myspots: [], // マイスポットのリスト
        dialog_params: {}, // モーダルダイアログの入出力パラメタ
        map2_markers: [], // Map2に配置のマーカ
        default_latlng: new google.maps.LatLng(default_lat, default_lng), // デフォルトのロケーション(現在地に上書き)
        travelmode: 'walking', // GoogleMapに指定するtravelmode
    },
    computed: {
        // ボタンに表示するテキスト
        orientation_text: function(){
            if( !this.destination_completed )
                return 'チェックポイントに到着しましたか？';

            if( this.origin_index == 0 )
                return 'オリエンテーション開始';
            else if( this.origin_index >= (this.checkpoints.length - 1) )
                return '最終目的地に到着しました。';
            else
                return '次のチェックポイントに出発';
        }
    },
    watch: {
        checkpoints: function(){
            // Mapに配置のマーカを再設定
            for( var i = 0 ; i < this.map_markers.length; i++ )
                this.map_markers[i].setMap(null);
            this.map_markers = [];
            for( var i = 0 ; i < this.checkpoints.length ; i++ ){
                var latlng = new google.maps.LatLng(this.checkpoints[i].lat, this.checkpoints[i].lng); 
                var mopts = {
                    position: latlng,
                    map: this.map,
                    label: String(i + 1)
                };
                var marker = new google.maps.Marker(mopts);
                this.map_markers.push(marker);
            }

            try{
                // サーバに同期
                update_data('checkpoints', this.checkpoints);
            }catch(error){
                this.toast_show("サーバにデータをアップロードできませんでした。");
            };
        },
        myspots: function(){
            try{
                // サーバに同期
                update_data('myspots', this.myspots);
            }catch(error){
                this.toast_show("サーバにデータをアップロードできませんでした。");
            };
        },
        origin_index: function(){
            try{
                // サーバに同期
                update_data('orientation', { origin_index: this.origin_index, destination_completed: this.destination_completed });
            }catch(error){
                this.toast_show("サーバにデータをアップロードできませんでした。");
            };
        },
        destination_completed: function(){
            try{
                // サーバに同期
                update_data('orientation', { origin_index: this.origin_index, destination_completed: this.destination_completed });
            }catch(error){
                this.toast_show("サーバにデータをアップロードできませんでした。");
            };
        },
    },
    methods: {
        // デフォルトのロケーションに移動
        map_goto_current_location: function(){
            var latlng = this.default_latlng;
            this.map.setCenter(latlng);                
        },
        // オリエンテーションタブ選択時にデフォルトのロケーションまたは出発位置に移動
        orientation_update_view: function(){
            var latlng = this.default_latlng;
            if( this.checkpoints.length > 0 )
                latlng = new google.maps.LatLng(this.checkpoints[this.origin_index].lat, this.checkpoints[this.origin_index].lng);
            this.map.setCenter(latlng);                
        },
        // オリエンテーションを指定位置からリスタート
        orientation_restart: function(index){
            if( index < 0 )
                if( !window.confirm('本当に最初から初めてもいいですか？') )
                    return;

            this.origin_index = (index < 0) ? 0 : index;
            this.destination_completed = true;
            this.orientation_next();
        },
        // 次の目的地(経由地を除く)を取得
        get_next_destination: function(){
            var destination_index = this.origin_index + 1;
            for( ; destination_index < this.checkpoints.length ; destination_index++ )
                if( !this.checkpoints[destination_index].waypoint )
                    break;
            if( destination_index >= this.checkpoints.length )
                destination_index = this.checkpoints.length - 1;
            return destination_index;
        },
        // 次へのボタンを押下
        orientation_next: function(){
            if( this.checkpoints.length == 0 ){
                alert('チェックポイントを追加してください。');
                return;
            }else if( this.checkpoints.length == 1 ){
                alert('次のチェックポイントを追加してください。');
                return;
            }
            
            if( this.origin_index >= (this.checkpoints.length - 1) ){
                alert('すでに目的地に到達しています。');
                return;
            }

            var destination_index = this.get_next_destination();
            if( (destination_index - (this.origin_index + 1)) > 9){
                alert('経由地の数が多すぎます。(9以下)');
                return;
            }

            if( !this.destination_completed ){
                // チェックポイントに到達
                this.origin_index = destination_index;
                this.destination_completed = true;

                // 目的位置に到達
                if( destination_index >= (this.checkpoints.length - 1) )
                    this.dialog_open('#orientation_complete_dialog');

                return;
            }

            // GoogleMap起動のパラメータを生成
            var params = "";
            var origin = this.checkpoints[this.origin_index];
            params += "&travelmode=" + this.travelmode;
            params += "&origin=" + encodeURIComponent(origin.lat + ',' + origin.lng);
            if( destination_index <= (this.checkpoints.length - 1)){
                var destination = this.checkpoints[destination_index];
                params += "&destination=" + encodeURIComponent(destination.lat + ',' + destination.lng);
            }
            if( (this.origin_index + 1) < destination_index ){
                var waypoints = "";
                for( var i = (this.origin_index + 1) ; i < destination_index ; i++ ){
                    if( i != (this.origin_index + 1) ) waypoints += '|';
                    waypoints += this.checkpoints[i].lat + ',' + this.checkpoints[i].lng;
                }
                params += "&waypoints=" + encodeURIComponent(waypoints);
            }
            var href = 'https://www.google.com/maps/dir/?api=1' + params;
            console.log(href);
            this.destination_completed = false;
            // GoogleMapを起動
            window.open(href, '_blank');
        },

        // Map2のマーカをクリアし、指定場所に移動
        map2_cleanup: function(latlng){
            for( var i = 0 ; i < this.map2_markers.length; i++ )
                this.map2_markers[i].setMap(null);
            this.map2_markers = [];
            if( this.map2_default_marker ){
                this.map2_default_marker.setMap(null);
                this.map2_default_marker = null;
            }
            this.map2.setCenter(latlng);
        },
        // モーダルダイアログの結果処理
        dialog_submit: function(){
            if( this.dialog_params.title == 'マイスポットの追加' ){
                var location = this.map2.getCenter();
                var name = this.dialog_params.name;
                this.myspots.push({
                    name, lat: location.lat(), lng: location.lng()
                });
            }else if( this.dialog_params.title == 'チェックポイントの追加' ){
                var location = this.map2.getCenter();
                var name = this.dialog_params.name;
                this.checkpoints.push({
                    name, lat: location.lat(), lng: location.lng()
                });
            }else if( this.dialog_params.title == 'マイスポットの変更'){
                var location = this.map2.getCenter();
                var point = this.myspots[this.dialog_params.index];
                point.lat = location.lat();
                point.lng = location.lng();
                Vue.set(this.myspots, this.dialog_params.index, point);
            }else if( this.dialog_params.title == 'チェックポイントの変更' ){
                var location = this.map2.getCenter();
                var point = this.checkpoints[this.dialog_params.index];
                point.lat = location.lat();
                point.lng = location.lng();
                Vue.set(this.checkpoints, this.dialog_params.index, point);
            }
            this.dialog_close('#select_location_dialog');
        },

        // マイスポットの追加(地図から)のためのモーダルダイアログ表示
        do_myspot_append: function(){
            this.map2_cleanup(this.default_latlng);
            this.map2_default_marker = new google.maps.Marker({
                position: this.default_latlng,
                map: this.map2,
            });

            this.dialog_params = {
                title: 'マイスポットの追加',
                is_input_name: true,
                is_input_submit: true,
            };
            this.dialog_open('#select_location_dialog');
        },
        // マイスポットの削除
        do_myspot_delete: function(index){
            if( !window.confirm('本当に削除していいですか？') )
                return;

            Vue.delete(this.myspots, index);
        },
        // マイスポットの名前変更
        do_myspot_change_name: function(index){
            var name = window.prompt('新しい名前', this.myspots[index].name);
            if( !name )
                return;
            this.myspots[index].name = name;
        },
        // マイスポットのロケーション変更
        do_myspot_change_location: function(index){
            var latlng = new google.maps.LatLng(this.myspots[index].lat, this.myspots[index].lng);
            this.map2_cleanup(latlng);
            this.map2_default_marker = new google.maps.Marker({
                position: latlng,
                map: this.map2,
            });

            this.dialog_params = {
                title: 'マイスポットの変更',
                index: index,
                is_input_submit: true,
            };
            this.dialog_open('#select_location_dialog');
        },

        // チェックポイントリストのリセット
        do_checkpoints_reset: function(){
            if( !window.confirm('本当にリセットしていいですか？') )
                return;
            this.origin_index = 0;
            this.destination_completed = true;
            this.checkpoints = [];
        },
        // チェックポイントの追加(マイスポットから)のためのモーダルダイアログ表示
        do_checkpoint_append_myspot: function(){
            if( this.myspots.length == 0 ){
                alert('マイスポットが登録されていません。');
                return;
            }

            this.map2_cleanup(this.default_latlng);

            this.dialog_params = {
                title: 'チェックポイントの追加(マイスポット)',
            };
            var _this = this;
            for( var i = 0 ; i < this.myspots.length ; i++ ){
                var mopts = {
                  position: new google.maps.LatLng(this.myspots[i].lat, this.myspots[i].lng),
                  map: this.map2,
                };
                var marker = new google.maps.Marker(mopts);
                this.map2_markers.push(marker);
                marker.addListener('click', function(e){
                    for( var i = 0 ; i < _this.map2_markers.length ; i++ ){
                        if( _this.map2_markers[i] == this ){
                            _this.checkpoints.push(_this.myspots[i]);
                            _this.dialog_close('#select_location_dialog');
                            return;
                        }
                    }
                });
            }
            this.dialog_open('#select_location_dialog');
        },
        // チェックポイント追加(地図から)のためのモーダルダイアログ表示
        do_checkpoint_append: function(){
            this.map2_cleanup(this.default_latlng);
            this.map2_default_marker = new google.maps.Marker({
                position: this.default_latlng,
                map: this.map2,
            });

            this.dialog_params = {
                title: 'チェックポイントの追加',
                is_input_name: true,
                is_input_submit: true,
                name : (this.checkpoints.length == 0) ? '現在地' : '',
            };
            this.dialog_open('#select_location_dialog');
        },
        // チェックポイントの削除
        do_checkpoint_delete: function(index){
            if( !window.confirm('本当に削除していいですか？') )
                return;

            Vue.delete(this.checkpoints, index);
        },
        // チェックポイントの名前変更
        do_checkpoint_change_name: function(index){
            var name = window.prompt('新しい名前', this.checkpoints[index].name);
            if( !name )
                return;
            this.checkpoints[index].name = name;
        },
        // チェックポイントのロケーション変更
        do_checkpoint_change_location: function(index){
            var latlng = new google.maps.LatLng(this.checkpoints[index].lat, this.checkpoints[index].lng);
            this.map2_cleanup(latlng);
            this.map2_default_marker = new google.maps.Marker({
                position: latlng,
                map: this.map2,
            });

            this.dialog_params = {
                title: 'チェックポイントの変更',
                index: index,
                is_input_submit: true,
            };
            this.dialog_open('#select_location_dialog');
        },
        // チェックポイントの順番変更
        do_checkpoint_change_index: function(index, event){
            var newIndex = event.target.selectedIndex;
            var temp = this.checkpoints[index];
            Vue.set(this.checkpoints, index, this.checkpoints[newIndex]);
            Vue.set(this.checkpoints, newIndex, temp);
        },
    },
    created: function(){
    },
    mounted: function(){
        proc_load();

        // 現在地情報の取得
        navigator.geolocation.getCurrentPosition((pos) =>{
            this.default_latlng = new google.maps.LatLng(pos.coords.latitude, pos.coords.longitude);
            this.map_goto_current_location();
        }, (error) =>{
            this.toast_show("現在地を取得できませんでした。");
        });

        // Mapの生成
        var myOptions = { 
          zoom: 15, 
          center: this.default_latlng, 
          mapTypeId: google.maps.MapTypeId.ROADMAP,
          mapTypeControl: false,
          streetViewControl: false,
        }; 
        var canvas = $('#map_canvas')[0];
        this.map = new google.maps.Map(canvas, myOptions);

        // Map2(モーダルダイアログ用)の生成
        var canvas2 = $('#map_canvas2')[0];
        this.map2 = new google.maps.Map(canvas2, myOptions);
        google.maps.event.addListener(this.map2, 'center_changed', () =>{
            if( !this.map2_default_marker )
                return;
            var location = this.map2.getCenter();
            this.map2_default_marker.setPosition(location);
        });

        // サーバ保持データの取得
        get_data('myspots')
        .then(data =>{
            this.myspots = data;
            return get_data('checkpoints');
        })
        .then(data => {
            this.checkpoints = data;
            return get_data('orientation');
        })
        .then(data => {
            if( data.origin_index != undefined )
                this.origin_index = data.origin_index;
            if( data.destination_completed != undefined )
                this.destination_completed = data.destination_completed;
        })
        .catch(error =>{
            this.toast_show("サーバからデータを取得できませんでした。");
        });
    }
};
vue_add_methods(vue_options, methods_bootstrap);
vue_add_components(vue_options, components_bootstrap);
var vue = new Vue( vue_options );

function do_post(url, body) {
    const headers = new Headers({ "Content-Type": "application/json; charset=utf-8" });
  
    return fetch(new URL(url).toString(), {
        method: 'POST',
        body: JSON.stringify(body),
        headers: headers
      })
      .then((response) => {
        if (!response.ok)
          throw 'status is not 200';
        return response.json();
      });
}

async function get_data(type){
    return do_post(base_url + '/get-data', { type: type })
    .then(json =>{
        if( json.status != 'OK' )
            throw "post failed";
        return json.result.data;
    });
}

async function update_data(type, data){
    var body = {
        type: type,
        data: data,
    }
    return do_post(base_url + '/update-data', body)
    .then(json =>{
        if( json.status != 'OK' )
            throw "post failed";
    });
}
