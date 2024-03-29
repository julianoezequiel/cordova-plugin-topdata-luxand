# Topdata - Cordova plugin para Luxand FaceSDK
Reconhecimento facial multiplataforma usando a estrutura Luxand FaceSDK

# Plataformas suportadas:
Este plugin suporta as seguintes plataformas:

* IOS
* Android

## Instalação

`npm install https://github.com/topdatadev/cordova-plugin-topdata-luxand.git`
<br/>
<br/>
`ionic cordova plugin add https://github.com/topdatadev/cordova-plugin-topdata-luxand.git`

## Uso
Antes de usar o plugin, você deve iniciá-lo com sua chave de licença da luxand, chamando o método `init`:

```js
Luxand.init({
    licence: "",
    loginTryCount: 3,
    dbname: "test.dat"
}, r=>{}, err=>{});
```
-  `licence` é a sua chave de licença
-  `loginTryCount` é o número de repetições para o processo de login e registro
-  `dbname` é onde os modelos de faces de arquivo locais serão salvos.

Este plugin permite que você registre um usuário usando seu modelo de rosto e o reconheça posteriormente. O registro é realizado uma vez e apenas uma vez para um usuário. Para registrar um usuário chame o método `register` no plugin:

```js
Luxand.register({
  timeout: <timeout>
}, <template>, <liveness>, <matchFaces>).then(async (r) => {
  console.log("Cadastro da face realizado com sucesso", r);
}).catch(async (err) => {
  console.log("Falha ao cadastrar face", err);
});
```

- O parâmetro `timeout` é o número de milissegundos a partir do qual o plugin deve retornar se nenhum rosto for detectado.
- O parâmetro `template` template do rosto.
- O parâmetro `liveness` parâmetro de vivacidade (Ex: 0.7).
- O parâmetro `matchFaces` parâmetro de similaridade entre os templates (Ex: 0.95).

Para comparar faces, utilize o método `compare` method on the plugin like this

```js
Luxand.compare({
  timeout: <timeout>
}, <template>, <liveness>, <matchFaces>).then(async (r) => {
  console.log("Comparação da face realizado com sucesso", r);
}).catch(async (err) => {
  console.log("Falha ao comparar face", err);
});
```

- O parâmetro `timeout` é o número de milissegundos a partir do qual o plugin deve retornar se nenhum rosto for detectado.
- O parâmetro `template` template do rosto.
- O parâmetro `liveness` parâmetro de vivacidade (Ex: 0.7).
- O parâmetro `matchFaces` parâmetro de similaridade entre os templates (Ex: 0.95).

Para limpar a memória do plugin, utilize o método `clearMemory` (limpeza feita no arquivo local onde os modelos de rostos são armazenados)
```js
Luxand.clearMemory((r)=>{}, err=>{});
```

Para limpar um rosto específico, utilize o método `clear`, passando o ID do rosto que você obteve do método de 'compare' ou 'register'
```js
Luxand.clear(id,(r)=>{}, err=>{});
```
## Desenvolvedores
- [Vinicius Felipe](https://github.com/Vinicius-Felipe-T)
